package com.Info.infoCenter;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "units")
public class Unit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String armortype;

    @Column(nullable = false)
    private int health;

    @Column(nullable = false)
    private int cost;

    @Column(nullable = false)
    private int speed;

    private String revealsShroudRange;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "unit_weapons", joinColumns = @JoinColumn(name = "unit_id"))
    @Column(name = "weapon_name")
    private List<String> weapons = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    @JsonIgnore
    private Profile profile;

    @Transient
    private int damage;

    public Unit() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getArmortype() { return armortype; }
    public void setArmortype(String armortype) { this.armortype = armortype; }

    public int getHealth() { return health; }
    public void setHealth(int health) { this.health = health; }

    public int getCost() { return cost; }
    public void setCost(int cost) { this.cost = cost; }

    public int getSpeed() { return speed; }
    public void setSpeed(int speed) { this.speed = speed; }

    public String getRevealsShroudRange() { return revealsShroudRange; }
    public void setRevealsShroudRange(String revealsShroudRange) { this.revealsShroudRange = revealsShroudRange; }

    public List<String> getWeapons() { return weapons; }
    public void setWeapons(List<String> weapons) { this.weapons = weapons; }

    public Profile getProfile() { return profile; }
    public void setProfile(Profile profile) { this.profile = profile; }

    public int getDamage() { return damage; }
    public void setDamage(int damage) { this.damage = damage; }
}
