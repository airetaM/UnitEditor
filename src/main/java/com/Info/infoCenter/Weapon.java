package com.Info.infoCenter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "weapons")
public class Weapon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private int reloadDelay;
    private String range;
    private int burst;

    // Projectile
    private int projectileSpeed;
    private int launchAngle;
    private String inaccuracy;
    private int contrailLength;

    // Warhead@1Dam
    private int spread;
    private int damage;

    // Versus
    private int versusNone;
    private int versusWood;
    private int versusLight;
    private int versusHeavy;
    private int versusConcrete;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    @JsonIgnore
    private Profile profile;

    public Weapon() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getReloadDelay() { return reloadDelay; }
    public void setReloadDelay(int reloadDelay) { this.reloadDelay = reloadDelay; }
    public String getRange() { return range; }
    public void setRange(String range) { this.range = range; }
    public int getBurst() { return burst; }
    public void setBurst(int burst) { this.burst = burst; }
    public int getProjectileSpeed() { return projectileSpeed; }
    public void setProjectileSpeed(int projectileSpeed) { this.projectileSpeed = projectileSpeed; }
    public int getLaunchAngle() { return launchAngle; }
    public void setLaunchAngle(int launchAngle) { this.launchAngle = launchAngle; }
    public String getInaccuracy() { return inaccuracy; }
    public void setInaccuracy(String inaccuracy) { this.inaccuracy = inaccuracy; }
    public int getContrailLength() { return contrailLength; }
    public void setContrailLength(int contrailLength) { this.contrailLength = contrailLength; }
    public int getSpread() { return spread; }
    public void setSpread(int spread) { this.spread = spread; }
    public int getDamage() { return damage; }
    public void setDamage(int damage) { this.damage = damage; }
    public int getVersusNone() { return versusNone; }
    public void setVersusNone(int versusNone) { this.versusNone = versusNone; }
    public int getVersusWood() { return versusWood; }
    public void setVersusWood(int versusWood) { this.versusWood = versusWood; }
    public int getVersusLight() { return versusLight; }
    public void setVersusLight(int versusLight) { this.versusLight = versusLight; }
    public int getVersusHeavy() { return versusHeavy; }
    public void setVersusHeavy(int versusHeavy) { this.versusHeavy = versusHeavy; }
    public int getVersusConcrete() { return versusConcrete; }
    public void setVersusConcrete(int versusConcrete) { this.versusConcrete = versusConcrete; }
    public Profile getProfile() { return profile; }
    public void setProfile(Profile profile) { this.profile = profile; }
}
