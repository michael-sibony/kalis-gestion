'use strict';

const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const EUROS = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' });
const NOMBRE = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 });
const euros = v => EUROS.format(Number(v));
const signe = v => {
  const n = Number(v);
  if (Math.abs(n) < 0.005) return EUROS.format(0);
  return (n > 0 ? '+' : '−') + ' ' + EUROS.format(Math.abs(n));
};
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const classe = c => esc(c).replace(/\./g, '.<wbr>');

const etat = {
  instances: [],
  code: null,
  articles: [],
  quantites: {},
  mode: 'avec',
  dernier: null,
  vue: 'chiffrage',
  jeton: 0,
  csvModifie: false,
  rapportValide: false,
  exemple: 0,
  migrationPrete: false
};

async function api(chemin, options = {}) {
  const r = await fetch('/api' + chemin, options);
  const texte = await r.text();
  let corps = null;
  try { corps = texte ? JSON.parse(texte) : null; } catch { corps = null; }
  if (!r.ok) throw new Error((corps && corps.erreur) || texte || ('Erreur ' + r.status));
  return corps;
}
const json = (methode, corps) => ({ method: methode, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(corps) });
const texte = (methode, corps) => ({ method: methode, headers: { 'Content-Type': 'text/plain; charset=utf-8' }, body: corps });

let minuteurToast;
function toast(message, erreur = false) {
  const t = $('#toast');
  t.textContent = message;
  t.classList.toggle('erreur', erreur);
  t.classList.add('visible');
  clearTimeout(minuteurToast);
  minuteurToast = setTimeout(() => t.classList.remove('visible'), erreur ? 4200 : 2200);
}

function memoriser(cle, valeur) { try { localStorage.setItem(cle, valeur); } catch { /* stockage indisponible */ } }
function relire(cle) { try { return localStorage.getItem(cle); } catch { return null; } }

/* ---------- Démarrage ---------- */

async function demarrer() {
  verifierSante();
  setInterval(verifierSante, 30000);
  try {
    etat.instances = await api('/instances');
  } catch (e) {
    $('#contenu').innerHTML = '<div class="carte vide">Serveur injoignable : ' + esc(e.message) + '</div>';
    return;
  }
  const select = $('#instance');
  select.innerHTML = etat.instances.map(i => `<option value="${esc(i.code)}">${esc(i.raisonSociale)} (${esc(i.ville)})</option>`).join('');
  const memo = relire('kalis.instance');
  const initial = etat.instances.some(i => i.code === memo) ? memo : (etat.instances.find(i => i.code === 'NEGOCE') || etat.instances[0]).code;
  select.value = initial;
  select.addEventListener('change', () => changerInstance(select.value));

  $$('#mode-chiffrage button').forEach(b => b.addEventListener('click', () => {
    etat.mode = b.dataset.mode;
    $$('#mode-chiffrage button').forEach(x => x.classList.toggle('actif', x === b));
    afficherPiece();
  }));
  $('#vider-panier').addEventListener('click', () => {
    etat.quantites = {};
    afficherCatalogue();
    chiffrer();
  });
  $('#reinitialiser').addEventListener('click', reinitialiser);
  $('#controler').addEventListener('click', () => controler(false));
  $('#integrer').addEventListener('click', () => controler(true));
  $('#csv').addEventListener('input', () => { etat.csvModifie = true; etat.rapportValide = false; $('#integrer').disabled = true; });
  $('#fichier').addEventListener('change', chargerFichier);
  $('#convertir').addEventListener('click', convertir);
  $('#copier').addEventListener('click', copier);
  $('#rafraichir').addEventListener('click', chargerPlateforme);
  window.addEventListener('hashchange', () => afficherVue(lireVue()));

  await changerInstance(initial);
  afficherVue(lireVue());
}

async function verifierSante() {
  const el = $('#etat-serveur');
  try {
    const s = await api('/sante');
    el.className = 'etat ok';
    el.querySelector('span').textContent = 'Serveur opérationnel';
  } catch {
    el.className = 'etat ko';
    el.querySelector('span').textContent = 'Serveur indisponible';
  }
}

function lireVue() {
  const v = location.hash.replace('#', '');
  return ['chiffrage', 'parametrage', 'reprise', 'migration', 'plateforme'].includes(v) ? v : 'chiffrage';
}

function afficherVue(vue) {
  etat.vue = vue;
  $$('.vue').forEach(s => { s.hidden = s.dataset.vue !== vue; });
  $$('#menu a').forEach(a => a.classList.toggle('actif', a.dataset.vue === vue));
  if (vue === 'parametrage') chargerParametres();
  if (vue === 'migration' && !etat.migrationPrete) preparerMigration();
  if (vue === 'plateforme') chargerPlateforme();
  if (vue === 'reprise' && !etat.csvModifie) $('#csv').value = EXEMPLES_CSV[etat.code] || '';
  window.scrollTo(0, 0);
}

function instanceCourante() {
  return etat.instances.find(i => i.code === etat.code);
}

async function changerInstance(code) {
  etat.code = code;
  memoriser('kalis.instance', code);
  const i = instanceCourante();
  etat.quantites = { ...(i.panierType || {}) };
  etat.csvModifie = false;
  etat.rapportValide = false;
  $('#csv').value = EXEMPLES_CSV[code] || '';
  $('#rapport').innerHTML = '';
  $('#compteurs').innerHTML = '';
  $('#etat-reprise').textContent = '';
  $('#integrer').disabled = true;
  afficherInfos();
  await chargerArticles();
  chiffrer();
  if (etat.vue === 'parametrage') chargerParametres();
}

function afficherInfos() {
  const i = instanceCourante();
  $('#infos-instance').innerHTML = `
    <span class="puce">${esc(i.secteur)}</span>
    <span class="puce orange">${esc(i.miseEnService)}</span>
    <span class="puce">${i.articles} articles</span>
    <span class="puce violet">${i.specifiques} règles spécifiques</span>`;
}

async function rafraichirInstance() {
  const i = await api('/instances/' + etat.code);
  etat.instances = etat.instances.map(x => x.code === i.code ? i : x);
  afficherInfos();
}

/* ---------- Chiffrage ---------- */

async function chargerArticles() {
  etat.articles = await api('/instances/' + etat.code + '/articles');
  afficherCatalogue();
}

function afficherCatalogue() {
  const c = $('#catalogue');
  c.innerHTML = etat.articles.map(a => {
    const q = etat.quantites[a.code] || 0;
    return `<div class="article ${q ? '' : 'zero'}" data-code="${esc(a.code)}">
      <div>
        <div class="nom">${esc(a.libelle)}</div>
        <div class="meta"><code>${esc(a.code)}</code><span>${euros(a.prixHt)} HT / ${esc(a.unite)}</span><span>${esc(a.famille.toLowerCase())}</span></div>
      </div>
      <input type="range" min="0" max="400" step="1" value="${q}" aria-label="Quantité ${esc(a.libelle)}">
      <input type="number" min="0" max="100000" step="1" value="${q}" aria-label="Quantité saisie ${esc(a.libelle)}">
    </div>`;
  }).join('') || '<div class="vide">Catalogue vide.</div>';
  $$('.article', c).forEach(ligne => {
    const code = ligne.dataset.code;
    const curseur = $('input[type=range]', ligne);
    const saisie = $('input[type=number]', ligne);
    const maj = v => {
      const q = Math.max(0, Math.min(100000, parseInt(v, 10) || 0));
      if (q) etat.quantites[code] = q; else delete etat.quantites[code];
      ligne.classList.toggle('zero', !q);
      chiffrerBientot();
      return q;
    };
    curseur.addEventListener('input', () => { saisie.value = maj(curseur.value); });
    saisie.addEventListener('input', () => { const q = maj(saisie.value); curseur.value = Math.min(q, 400); });
  });
}

let minuteurChiffrage;
function chiffrerBientot() {
  clearTimeout(minuteurChiffrage);
  minuteurChiffrage = setTimeout(chiffrer, 120);
}

async function chiffrer() {
  const jeton = ++etat.jeton;
  try {
    const d = await api('/instances/' + etat.code + '/chiffrage', json('POST', { quantites: etat.quantites }));
    if (jeton !== etat.jeton) return;
    etat.dernier = d;
    afficherPiece();
    afficherResume();
  } catch (e) {
    if (jeton === etat.jeton) toast(e.message, true);
  }
}

function afficherPiece() {
  const d = etat.dernier;
  if (!d) return;
  const r = etat.mode === 'avec' ? d.avecSpecifiques : d.coeurSeul;
  const i = instanceCourante();
  const ecartHt = Number(d.ecartHt);
  const ecartTtc = Number(d.ecartTtc);
  let corps;
  if (!r.lignes.length) {
    corps = '<div class="vide">Réglez des quantités dans le catalogue pour chiffrer une pièce.</div>';
  } else {
    const lignes = r.lignes.map(l => {
      const qte = l.quantite !== l.quantiteDemandee
        ? `<span class="qte-modifiee">${l.quantiteDemandee} → ${l.quantite}</span>`
        : String(l.quantite);
      const ajust = [];
      if (Number(l.remise)) ajust.push(`<span class="remise">${signe(-l.remise)}</span>`);
      if (Number(l.majoration)) ajust.push(`<span class="${Number(l.majoration) > 0 ? 'hausse' : 'remise'}">${signe(l.majoration)}</span>`);
      return `<tr>
        <td>${esc(l.libelle)}<span class="sous">${esc(l.code)}</span></td>
        <td class="n">${qte}<span class="sous">${esc(l.unite)}</span></td>
        <td class="n col-pu">${euros(l.prixUnitaireHt)}</td>
        <td class="n">${ajust.join('<br>') || '<span class="sous">aucun</span>'}</td>
        <td class="n">${euros(l.montantNetHt)}</td>
      </tr>`;
    }).join('');
    const ajustements = r.ajustements.map(a => `<tr class="ajust">
        <td colspan="3">${esc(a.libelle)}<span class="sous">${esc(a.source)}</span></td>
        <td class="n col-pu"></td><td class="n">${euros(a.montantHt)}</td></tr>`).join('');
    const tva = r.exonereTva
      ? `<div><span>TVA</span><span>exonérée</span></div>`
      : r.tva.map(t => `<div><span>TVA ${NOMBRE.format(t.taux)} % sur ${euros(t.base)}</span><span>${euros(t.montant)}</span></div>`).join('');
    corps = `
      <div class="table-defile"><table class="table">
        <thead><tr><th>Désignation</th><th class="n">Qté</th><th class="n col-pu">PU HT</th><th class="n">Remise</th><th class="n">Net HT</th></tr></thead>
        <tbody>${lignes}${ajustements}</tbody>
      </table></div>
      <div class="totaux">
        <div><span>Total brut HT</span><span>${euros(r.totalBrutHt)}</span></div>
        ${Number(r.totalRemises) ? `<div><span>Remises</span><span class="remise">${signe(-r.totalRemises)}</span></div>` : ''}
        ${Number(r.totalMajorations) ? `<div><span>Indexation</span><span>${signe(r.totalMajorations)}</span></div>` : ''}
        <div class="fort"><span>Total HT</span><span>${euros(r.totalHt)}</span></div>
        ${tva}
        <div class="fort ttc"><span>Total TTC</span><span>${euros(r.totalTtc)}</span></div>
      </div>
      ${r.mentions.length ? `<div class="mentions">${r.mentions.map(m => `<span>${esc(m)}</span>`).join('')}</div>` : ''}`;
  }
  const couleur = ecartHt < 0 ? 'remise' : ecartHt > 0 ? 'hausse' : '';
  $('#piece').innerHTML = `
    <div class="tete-piece">
      <div>
        <span class="ref">${etat.mode === 'avec' ? 'Devis avec règles du client' : 'Devis calculé par le cœur seul'}</span>
        <strong>${esc(i.raisonSociale)}</strong>
        <span class="ref">${esc(i.ville)}</span>
      </div>
      <div class="ecart">
        <div class="valeur ${couleur}">${signe(ecartHt)} HT</div>
        <div class="libelle">effet des spécifiques${Math.abs(ecartTtc - ecartHt) >= 0.005 ? `, soit ${signe(ecartTtc)} TTC` : ''}</div>
      </div>
    </div>${corps}`;

  $('#nb-regles').textContent = Math.max(0, r.traces.filter(t => t.declenchee).length) + ' déclenchées sur ' + r.traces.length;
  $('#regles').innerHTML = r.traces.map(t => {
    const effet = Number(t.effetHt);
    const cls = effet < 0 ? 'remise' : effet > 0 ? 'hausse' : '';
    return `<li class="${t.declenchee ? '' : 'inactive'}">
      <span class="ordre">${t.ordre}</span>
      <div>
        <div class="titre">${esc(t.regle)} <span class="puce ${t.portee === 'Standard' ? 'bleu' : 'violet'}">${esc(t.portee)}</span></div>
        <div class="classe">${classe(t.classe)}</div>
        <div class="detail">${esc(t.detail)}</div>
      </div>
      <span class="effet ${cls}">${t.declenchee ? (effet ? signe(effet) : 'sans effet HT') : 'non déclenchée'}</span>
    </li>`;
  }).join('');
}

function afficherResume() {
  const d = etat.dernier;
  if (!d) return;
  const r = d.avecSpecifiques;
  $('#resume-panier').innerHTML = `
    <div><span>Panier en cours HT</span><strong>${euros(r.totalHt)}</strong></div>
    <div><span>TTC</span><strong>${euros(r.totalTtc)}</strong></div>
    <div><span>Effet des spécifiques</span><strong class="${Number(d.ecartHt) < 0 ? 'remise' : Number(d.ecartHt) > 0 ? 'hausse' : ''}">${signe(d.ecartHt)}</strong></div>`;
}

/* ---------- Paramétrage ---------- */

function valeurLisible(v, unite) {
  const n = Number(v);
  if (unite === 'oui/non') return n ? 'Oui' : 'Non';
  if (unite === '€ HT') return euros(n) + ' HT';
  if (unite === 'unités' && Math.abs(n) <= 1) return NOMBRE.format(n) + ' unité';
  return NOMBRE.format(n) + ' ' + unite;
}

async function chargerParametres() {
  const code = etat.code;
  try {
    const groupes = await api('/instances/' + code + '/parametres');
    if (code !== etat.code) return;
    const avecReglages = groupes.filter(g => g.parametres.length);
    $('#parametres').innerHTML = avecReglages.map(g => `
      <div class="carte">
        <div class="entete-carte" style="align-items:flex-start">
          <div><h2>${esc(g.regle)}</h2><div class="mono" style="color:var(--discret);margin-top:3px">${classe(g.classe)}</div></div>
          <span class="puce ${g.portee === 'Standard' ? 'bleu' : 'violet'}">${esc(g.portee)}</span>
        </div>
        ${g.parametres.map(p => champParametre(p)).join('')}
      </div>`).join('');
    $$('#parametres .param').forEach(brancherParametre);
    chargerJournal();
    if (etat.dernier) afficherResume();
  } catch (e) {
    toast(e.message, true);
  }
}

function champParametre(p) {
  const d = p.definition;
  const id = 'p-' + d.cle;
  if (d.unite === 'oui/non') {
    return `<div class="param" data-cle="${esc(d.cle)}" data-unite="oui/non" data-valeur="${p.valeur}">
      <div class="ligne"><label for="${id}">${esc(d.libelle)}</label>
        <label class="interrupteur"><input type="checkbox" id="${id}" ${Number(p.valeur) ? 'checked' : ''}><span></span></label></div>
      <div class="bornes"><span>Par défaut : ${valeurLisible(d.defaut, d.unite)}</span></div>
    </div>`;
  }
  return `<div class="param" data-cle="${esc(d.cle)}" data-unite="${esc(d.unite)}" data-valeur="${p.valeur}">
    <div class="ligne"><label for="${id}">${esc(d.libelle)}</label><span class="valeur">${valeurLisible(p.valeur, d.unite)}</span></div>
    <input type="range" id="${id}" min="${d.min}" max="${d.max}" step="${d.pas}" value="${p.valeur}">
    <div class="bornes"><span>${valeurLisible(d.min, d.unite)}</span><span>défaut ${valeurLisible(d.defaut, d.unite)}</span><span>${valeurLisible(d.max, d.unite)}</span></div>
  </div>`;
}

function brancherParametre(bloc) {
  const cle = bloc.dataset.cle;
  const unite = bloc.dataset.unite;
  const champ = $('input', bloc);
  const affichage = $('.valeur', bloc);
  const lire = () => unite === 'oui/non' ? (champ.checked ? 1 : 0) : Number(champ.value);
  if (affichage) champ.addEventListener('input', () => { affichage.textContent = valeurLisible(champ.value, unite); });
  champ.addEventListener('change', async () => {
    const valeur = lire();
    try {
      const r = await api(`/instances/${etat.code}/parametres/${encodeURIComponent(cle)}`, json('PUT', { valeur }));
      bloc.dataset.valeur = valeur;
      bloc.classList.add('enregistre');
      setTimeout(() => bloc.classList.remove('enregistre'), 1200);
      toast(r.definition.libelle + ' : ' + valeurLisible(valeur, unite) + ', enregistré.');
      chiffrer();
      chargerJournal();
    } catch (e) {
      toast(e.message, true);
      if (unite === 'oui/non') champ.checked = Number(bloc.dataset.valeur) === 1;
      else { champ.value = bloc.dataset.valeur; if (affichage) affichage.textContent = valeurLisible(bloc.dataset.valeur, unite); }
    }
  });
}

async function chargerJournal() {
  const code = etat.code;
  const evenements = await api('/instances/' + code + '/journal');
  if (code !== etat.code) return;
  const date = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeStyle: 'medium', timeZone: 'Europe/Paris' });
  const couleur = { 'Paramétrage': 'bleu', 'Reprise': 'vert', 'Réinitialisation': 'orange', 'Installation': '' };
  $('#journal').innerHTML = evenements.map(e => `<li>
      <time>${date.format(new Date(e.horodatage))} <span class="puce ${couleur[e.nature] ?? ''}">${esc(e.nature)}</span></time>
      ${esc(e.message)}</li>`).join('') || '<li class="discret">Aucun événement.</li>';
}

async function reinitialiser() {
  const i = instanceCourante();
  if (!confirm(`Remettre ${i.raisonSociale} dans l'état de recette (catalogue et paramétrage) ?`)) return;
  try {
    await api('/instances/' + etat.code + '/reinitialisation', { method: 'POST' });
    toast('Instance remise à l\'état de recette.');
    await rafraichirInstance();
    etat.quantites = { ...(instanceCourante().panierType || {}) };
    await chargerArticles();
    chiffrer();
    chargerParametres();
  } catch (e) {
    toast(e.message, true);
  }
}

/* ---------- Reprise ---------- */

const EXEMPLES_CSV = {
  NEGOCE: `code;libelle;famille;unite;prix_ht;tva
PLQ-BA13;Plaque de plâtre BA13 2,50 m;PLAQUE;u;9,15;20
PLQ-HYDRO;Plaque hydrofuge BA13 2,50 m;PLAQUE;u;12,60;20
CORN-ALU;Cornière d'angle aluminium 2,50 m;FINITION;u;3,85;20
BANDE-150;Bande à joint papier, 150 m;FINITION;rouleau;4,90;20
ISO-LDR45;Laine de roche 45 mm, panneau;ISOLANT;u;sur devis;20
PLQ-HYDRO;Plaque hydrofuge BA13, ancien tarif;PLAQUE;u;11,90;20
TRAPPE-60;Trappe de visite 60 x 60;ACCES;u;48,00;19,6
SUSP-F530;Suspente pivot F530;OSSATURE;u;0,92;20`,
  VAUBAN: `code;libelle;famille;unite;prix_ht;tva
CBL-CU25;Câble cuivre 25 mm²;MATIERE;m;9,40;20
BAR-CU20;Barre cuivre Ø 20 mm;MATIERE;m;43,50;20
USN-PALIER;Palier usiné acier C45;USINAGE;u;87,00;20
USN-AXE40;Axe rectifié Ø 40 mm;USINAGE;u;-52,00;20
PRS-CTRL3D;Contrôle tridimensionnel, heure;SERVICE;h;95,00;20
TRT-ZING;Traitement zingage au kilo;SERVICE;kg;2,10;20;`,
  EXPORT: `code;libelle;famille;unite;prix_ht;tva
GNT-NIT200;Gants nitrile, boîte de 200;PROTECTION;boîte;14,90;20
SUR-BLOUSE;Surblouses non tissées, lot de 10;PROTECTION;lot;9,80;20
SER-10ML;Seringues 10 ml, carton de 100;INJECTION;carton;19,40;20
MSQ-FFP2;Masques FFP2, boîte de 20;PROTECTION;boîte;11,90;20
ALC-GEL500;Gel hydroalcoolique 500 ml;HYGIENE;flacon;4,20;5,5
TH-INFRA;Thermomètre infrarouge;DIAGNOSTIC;u;;20`
};

async function controler(integrer) {
  const contenu = $('#csv').value;
  const bouton = integrer ? $('#integrer') : $('#controler');
  bouton.disabled = true;
  try {
    const r = await api(`/instances/${etat.code}/reprise/${integrer ? 'integration' : 'analyse'}`, texte('POST', contenu));
    afficherRapport(r);
    if (integrer) {
      toast(`${r.creations + r.misesAJour} articles intégrés, ${r.rejets} lignes laissées de côté.`);
      etat.rapportValide = false;
      await rafraichirInstance();
      await chargerArticles();
      chiffrer();
    } else {
      etat.rapportValide = r.creations + r.misesAJour > 0;
    }
  } catch (e) {
    toast(e.message, true);
  } finally {
    $('#controler').disabled = false;
    $('#integrer').disabled = !etat.rapportValide;
  }
}

function afficherRapport(r) {
  $('#etat-reprise').textContent = r.integre ? 'Intégration terminée' : r.lignes.length + ' lignes lues';
  $('#compteurs').innerHTML = `
    <div class="compteur vert"><strong>${r.creations}</strong><span>${r.integre ? 'créés' : 'à créer'}</span></div>
    <div class="compteur bleu"><strong>${r.misesAJour}</strong><span>${r.integre ? 'mis à jour' : 'à mettre à jour'}</span></div>
    <div class="compteur rouge"><strong>${r.rejets}</strong><span>rejetées</span></div>`;
  const statut = { CREATION: ['vert', 'Création'], MISE_A_JOUR: ['bleu', 'Mise à jour'], REJET: ['rouge', 'Rejet'] };
  $('#rapport').innerHTML = `<thead><tr><th>Ligne</th><th>Article</th><th class="n">Prix HT</th><th class="n">TVA</th><th>Statut</th></tr></thead>
    <tbody>${r.lignes.map(l => {
      const [c, lib] = statut[l.statut];
      return `<tr class="${l.statut === 'REJET' ? 'rejet' : ''}">
        <td class="mono">${l.numero}</td>
        <td>${esc(l.libelle || '')}<span class="sous mono">${esc(l.code || '')}</span>${l.erreurs.length ? `<div class="erreurs">${l.erreurs.map(esc).join('<br>')}</div>` : ''}</td>
        <td class="n">${l.prixHt != null ? euros(l.prixHt) : ''}</td>
        <td class="n">${l.tauxTva != null ? NOMBRE.format(l.tauxTva) + ' %' : ''}</td>
        <td><span class="puce ${c}">${lib}</span></td></tr>`;
    }).join('')}</tbody>`;
}

function chargerFichier(ev) {
  const f = ev.target.files[0];
  if (!f) return;
  const lecteur = new FileReader();
  lecteur.onload = () => {
    $('#csv').value = lecteur.result;
    etat.csvModifie = true;
    controler(false);
  };
  lecteur.readAsText(f, 'utf-8');
  ev.target.value = '';
}

/* ---------- Migration ---------- */

const EXEMPLES_MIGRATION = [
  { titre: 'Service EJB, JPA et REST', fichier: 'CommandeService.java', contenu: `package com.editeur.ventes;

import java.util.List;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.naming.InitialContext;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;
import javax.transaction.xa.XAResource;
import javax.validation.Valid;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBContext;
import org.hibernate.Criteria;
import org.hibernate.Session;

@Stateless
@Path("/commandes")
public class CommandeService {

    @PersistenceContext(unitName = "ventes")
    private EntityManager em;

    @Resource(lookup = "java:jboss/datasources/VentesDS")
    private DataSource source;

    @Inject
    private TarifService tarifs;

    @PostConstruct
    void init() throws Exception {
        JAXBContext.newInstance(Commande.class);
    }

    @GET
    @Path("/en-retard")
    public List<Commande> enRetard() {
        Session session = em.unwrap(Session.class);
        Criteria criteres = session.createCriteria(Commande.class);
        return criteres.list();
    }

    @POST
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Response creer(@Valid Commande commande) {
        em.persist(commande);
        return Response.status(201).build();
    }
}` },
  { titre: 'persistence.xml', fichier: 'persistence.xml', contenu: `<?xml version="1.0" encoding="UTF-8"?>
<persistence xmlns="http://xmlns.jcp.org/xml/ns/persistence"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/persistence http://xmlns.jcp.org/xml/ns/persistence/persistence_2_2.xsd"
             version="2.2">
    <persistence-unit name="ventes" transaction-type="JTA">
        <provider>org.hibernate.ejb.HibernatePersistence</provider>
        <jta-data-source>java:jboss/datasources/VentesDS</jta-data-source>
        <properties>
            <property name="javax.persistence.schema-generation.database.action" value="none"/>
            <property name="javax.persistence.lock.timeout" value="3000"/>
            <property name="hibernate.dialect" value="org.hibernate.dialect.PostgreSQL95Dialect"/>
        </properties>
    </persistence-unit>
</persistence>` },
  { titre: 'beans.xml', fichier: 'beans.xml', contenu: `<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://xmlns.jcp.org/xml/ns/javaee"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/beans_2_0.xsd"
       version="2.0">
</beans>` },
  { titre: 'web.xml', fichier: 'web.xml', contenu: `<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/web-app_4_0.xsd"
         version="4.0">
    <context-param>
        <param-name>javax.faces.PROJECT_STAGE</param-name>
        <param-value>Production</param-value>
    </context-param>
    <servlet>
        <servlet-name>Faces Servlet</servlet-name>
        <servlet-class>javax.faces.webapp.FacesServlet</servlet-class>
    </servlet>
    <servlet-mapping>
        <servlet-name>Faces Servlet</servlet-name>
        <url-pattern>*.xhtml</url-pattern>
    </servlet-mapping>
</web-app>` },
  { titre: 'pom.xml', fichier: 'pom.xml', contenu: `<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.editeur</groupId>
    <artifactId>ventes</artifactId>
    <version>3.8.2</version>
    <packaging>war</packaging>
    <properties>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
    </properties>
    <dependencies>
        <dependency>
            <groupId>javax</groupId>
            <artifactId>javaee-api</artifactId>
            <version>8.0.1</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>javax.servlet</groupId>
            <artifactId>javax.servlet-api</artifactId>
            <version>4.0.1</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.hibernate</groupId>
            <artifactId>hibernate-core</artifactId>
            <version>5.4.33.Final</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>` }
];

function preparerMigration() {
  etat.migrationPrete = true;
  $('#exemples').innerHTML = EXEMPLES_MIGRATION.map((e, i) => `<button data-i="${i}">${esc(e.titre)}</button>`).join('');
  $$('#exemples button').forEach(b => b.addEventListener('click', () => choisirExemple(Number(b.dataset.i))));
  choisirExemple(0);
}

function choisirExemple(i) {
  etat.exemple = i;
  $$('#exemples button').forEach(b => b.classList.toggle('actif', Number(b.dataset.i) === i));
  $('#source').value = EXEMPLES_MIGRATION[i].contenu;
  $('#nom-fichier').value = EXEMPLES_MIGRATION[i].fichier;
  convertir();
}

async function convertir() {
  try {
    const r = await api('/outils/migration-jakarta', json('POST', { nomFichier: $('#nom-fichier').value, contenu: $('#source').value }));
    const modifiees = new Set(r.changements.map(c => c.ligne));
    $('#resultat').innerHTML = r.contenu.split('\n')
      .map((l, i) => `<span class="l${modifiees.has(i + 1) ? ' modif' : ''}" data-n="${i + 1}">${esc(l) || ' '}</span>`).join('');
    $('#resultat').dataset.texte = r.contenu;
    $('#nb-modifs').textContent = r.changements.length + (r.changements.length > 1 ? ' lignes modifiées' : ' ligne modifiée');
    $('#modifications').innerHTML = r.changements.map(c => `<li><span class="num">L${c.ligne}</span>
      <div>${esc(c.motif)}<div class="mono" style="color:var(--vert);overflow-wrap:anywhere;margin-top:2px">${esc(c.apres.trim())}</div></div></li>`).join('')
      || '<li><span class="num"></span><div class="discret">Rien à convertir.</div></li>';
    const nb = n => r.alertes.filter(a => a.niveau === n).length;
    $('#nb-alertes').textContent = `${nb('à reprendre')} à reprendre, ${nb('à vérifier')} à vérifier, ${r.conservesEnJavax} javax conservés`;
    const couleur = { 'à reprendre': 'rouge', 'à vérifier': 'orange', 'info': '' };
    $('#alertes').innerHTML = r.alertes.map(a => `<li><span class="num">L${a.ligne}</span>
      <div><span class="puce ${couleur[a.niveau] ?? ''}">${esc(a.niveau)}</span> ${esc(a.message)}</div></li>`).join('')
      || '<li><span class="num"></span><div class="discret">Aucun point d\'attention.</div></li>';
  } catch (e) {
    toast(e.message, true);
  }
}

async function copier() {
  try {
    await navigator.clipboard.writeText($('#resultat').dataset.texte || '');
    toast('Résultat copié.');
  } catch {
    toast('Copie refusée par le navigateur.', true);
  }
}

/* ---------- Plateforme ---------- */

function duree(s) {
  const j = Math.floor(s / 86400), h = Math.floor(s % 86400 / 3600), m = Math.floor(s % 3600 / 60);
  if (j) return `${j} j ${h} h`;
  if (h) return `${h} h ${m} min`;
  return `${m} min ${s % 60} s`;
}

async function chargerPlateforme() {
  try {
    const [p, s] = await Promise.all([api('/plateforme'), api('/sante').catch(() => ({ statut: 'DOWN', base: 'injoignable' }))]);
    const part = Math.round(100 * p.memoireUtiliseeMo / Math.max(1, p.memoireMaxMo));
    $('#tuiles').innerHTML = `
      <div class="tuile"><span>Serveur d'application</span><strong>${esc(p.serveur)}</strong></div>
      <div class="tuile"><span>Santé</span><strong>${s.statut === 'UP' ? '<span class="puce vert">UP</span>' : '<span class="puce rouge">DOWN</span>'}</strong><small>Base ${esc(s.base)}</small></div>
      <div class="tuile"><span>Java</span><strong>${esc(p.java)}</strong><small>${esc(p.jvm)}</small></div>
      <div class="tuile"><span>Servlet</span><strong>${esc(p.servlet)}</strong></div>
      <div class="tuile"><span>Base de données</span><strong>${esc(p.base.produit)}</strong><small>${esc(p.base.source || '')}</small></div>
      <div class="tuile"><span>Mémoire JVM</span><strong>${p.memoireUtiliseeMo} Mo sur ${p.memoireMaxMo} Mo</strong><div class="jauge"><i style="width:${part}%"></i></div></div>
      <div class="tuile"><span>Système</span><strong>${esc(p.systeme)}</strong><small>${p.processeurs} processeur${p.processeurs > 1 ? 's' : ''}</small></div>
      <div class="tuile"><span>En service depuis</span><strong>${duree(p.demarreDepuisSecondes)}</strong></div>`;
    $('#nb-cdi').textContent = p.regles.length + ' beans';
    $('#table-regles').innerHTML = `<thead><tr><th>Ordre</th><th>Règle</th><th>Portée</th></tr></thead><tbody>${p.regles
      .map(r => `<tr><td class="mono">${r.ordre}</td><td>${esc(r.libelle)}<span class="sous mono">${classe(r.classe)}</span></td>
        <td><span class="puce ${r.portee === 'Standard' ? 'bleu' : 'violet'}">${esc(r.instance || r.portee)}</span></td></tr>`).join('')}</tbody>`;
    $('#api').innerHTML = p.api.map(a => `<li>${esc(a)}</li>`).join('');
  } catch (e) {
    toast(e.message, true);
  }
}

demarrer();
