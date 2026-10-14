package fr.kalis.gestion.service;

public class InstanceInconnue extends RuntimeException {

    public InstanceInconnue(String code) {
        super("Aucune instance client « " + code + " ».");
    }
}
