package com.murisegames.api.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "games", schema = "murise")
public class Game {
    @Id
    private String code;
    private String name;
    private String rulesStatus;

    protected Game() {}

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getRulesStatus() { return rulesStatus; }
}
