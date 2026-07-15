package com.Info.infoCenter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

@Service
public class UnitService {

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private WeaponRepository weaponRepository;

    @Autowired
    private YamlFileRepository yamlFileRepository;

    public List<Unit> getUnitsByProfile(Profile profile) {
        return unitRepository.findByProfile(profile);
    }

    @SuppressWarnings("unchecked")
    public int parseAndAddUnits(String yamlContent, String fileName, Profile profile) {
        yamlFileRepository.save(new YamlFile(fileName, yamlContent, profile));

        Yaml yaml = new Yaml();
        Object data = yaml.load(sanitizeYaml(yamlContent));
        if (!(data instanceof Map<?, ?> root)) return 0;

        int count = 0;
        for (Map.Entry<?, ?> entry : root.entrySet()) {
            String name = entry.getKey().toString();
            if (name.startsWith("^") || name.startsWith("-")) continue;
            if (!(entry.getValue() instanceof Map<?, ?> def)) continue;

            Map<String, Object> merged = resolveInherited(
                    (Map<String, Object>) def, profile,
                    (Map<String, Object>) (Object) root);

            if (isWeapon(merged)) {
                Weapon weapon = buildWeapon(name, merged, profile);
                weaponRepository.save(weapon);
            } else if (isUnit(merged)) {
                Unit unit = buildUnit(name, merged, profile);
                unitRepository.save(unit);
                count++;
            }
        }
        return count;
    }

    private boolean isWeapon(Map<String, Object> traits) {
        for (String key : traits.keySet()) {
            if (key.equals("ReloadDelay") || key.startsWith("Warhead") || key.equals("Projectile")) return true;
        }
        return false;
    }

    private boolean isUnit(Map<String, Object> traits) {
        for (String key : traits.keySet()) {
            if (key.equals("Health") || key.equals("Mobile") || key.equals("Buildable")
                    || key.equals("Armor") || key.equals("Valued")) return true;
        }
        return false;
    }

    /** Find a definition by name in saved YamlFiles within the same profile. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> findInProfile(String name, Profile profile) {
        Yaml yaml = new Yaml();
        for (YamlFile file : yamlFileRepository.findByProfile(profile)) {
            try {
                Object data = yaml.load(sanitizeYaml(file.getContent()));
                if (data instanceof Map<?, ?> root && root.get(name) instanceof Map<?, ?> def) {
                    return (Map<String, Object>) def;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    @Transactional
    public void clearUnitsByProfile(Profile profile) {
        unitRepository.deleteByProfile(profile);
        weaponRepository.deleteByProfile(profile);
    }

    // --- Inheritance with deep merge ---

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveInherited(Map<String, Object> unitDef, Profile profile,
                                                  Map<String, Object> currentFileDefs) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Merge parent sections first (parents don't override each other)
        for (Map.Entry<String, Object> entry : unitDef.entrySet()) {
            String key = entry.getKey().toString();
            if (!key.startsWith("Inherits") || !(entry.getValue() instanceof String parentRef)) continue;

            Map<String, Object> parentDef = null;
            if (currentFileDefs != null && currentFileDefs.get(parentRef) instanceof Map<?, ?> m) {
                parentDef = (Map<String, Object>) m;
            } else {
                parentDef = findInProfile(parentRef, profile);
            }

            if (parentDef != null) {
                Map<String, Object> parentMerged = resolveInherited(parentDef, profile, currentFileDefs);
                for (Map.Entry<String, Object> p : parentMerged.entrySet()) {
                    result.putIfAbsent(p.getKey(), p.getValue());
                }
            }
        }

        // Apply own sections — deep merge: only override sub-keys, keep inherited sub-keys
        for (Map.Entry<String, Object> entry : unitDef.entrySet()) {
            String key = entry.getKey().toString();
            if (key.startsWith("Inherits")) continue;

            if (result.containsKey(key) && entry.getValue() instanceof Map<?, ?> ownMap
                    && result.get(key) instanceof Map<?, ?> existingMap) {
                // Deep merge: child's sub-keys override parent's, keep parent's missing sub-keys
                Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) existingMap);
                for (Map.Entry<?, ?> sub : ownMap.entrySet()) {
                    Object subVal = sub.getValue();
                    Object existingVal = existingMap.get(sub.getKey().toString());
                    // Don't override a non-zero parent value with 0 from child
                    if (isZeroValue(subVal) && existingVal != null && !isZeroValue(existingVal)) {
                        continue;
                    }
                    merged.put(sub.getKey().toString(), subVal);
                }
                result.put(key, merged);
            } else {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    // --- Build Unit from merged traits ---

    @SuppressWarnings("unchecked")
    private Unit buildUnit(String name, Map<String, Object> traits, Profile profile) {
        Unit unit = new Unit();
        unit.setName(name);
        unit.setProfile(profile);

        for (Map.Entry<String, Object> section : traits.entrySet()) {
            String sectionKey = section.getKey();
            if (!(section.getValue() instanceof Map<?, ?> sectionMap)) continue;

            switch (sectionKey) {
                case "Health" -> unit.setHealth(getIntValue(sectionMap, "HP") / 100);
                case "Armor" -> unit.setArmortype(getStringValue(sectionMap, "Type"));
                case "Mobile" -> unit.setSpeed(getIntValue(sectionMap, "Speed"));
                case "Valued" -> unit.setCost(getIntValue(sectionMap, "Cost"));
                case "RevealsShroud" -> unit.setRevealsShroudRange(getStringValue(sectionMap, "Range"));
                default -> {
                    if (sectionKey.startsWith("Armament") && !sectionKey.contains("@GARRISONED")
                            && sectionMap.get("Weapon") instanceof String wn) {
                        unit.getWeapons().add(wn);
                    }
                }
            }
        }
        return unit;
    }

    @SuppressWarnings("unchecked")
    private Weapon buildWeapon(String name, Map<String, Object> traits, Profile profile) {
        Weapon w = new Weapon();
        w.setName(name);
        w.setProfile(profile);

        for (Map.Entry<String, Object> section : traits.entrySet()) {
            String sectionKey = section.getKey();
            Object sectionVal = section.getValue();

            switch (sectionKey) {
                case "ReloadDelay" -> w.setReloadDelay(toInt(sectionVal));
                case "Range" -> w.setRange(sectionVal.toString());
                case "Burst" -> w.setBurst(toInt(sectionVal));
                default -> {
                    if (sectionVal instanceof Map<?, ?> sm) {
                        switch (sectionKey) {
                            case "Projectile" -> {
                                w.setProjectileSpeed(getIntValue(sm, "Speed"));
                                w.setLaunchAngle(getIntValue(sm, "LaunchAngle"));
                                w.setInaccuracy(getStringValue(sm, "Inaccuracy"));
                                w.setContrailLength(getIntValue(sm, "ContrailLength"));
                            }
                            default -> {
                                if (sectionKey.startsWith("Warhead") && sectionKey.contains("Dam")
                                        && sm.containsKey("Damage")) {
                                    w.setSpread(getIntValue(sm, "Spread"));
                                    w.setDamage(getIntValue(sm, "Damage"));
                                    if (sm.get("Versus") instanceof Map<?, ?> versus) {
                                        w.setVersusNone(getIntValue(versus, "None"));
                                        w.setVersusWood(getIntValue(versus, "Wood"));
                                        w.setVersusLight(getIntValue(versus, "Light"));
                                        w.setVersusHeavy(getIntValue(versus, "Heavy"));
                                        w.setVersusConcrete(getIntValue(versus, "Concrete"));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return w;
    }

    private int toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    private boolean isZeroValue(Object val) {
        if (val == null) return true;
        if (val instanceof Number n) return n.intValue() == 0;
        if (val instanceof String s) return s.isEmpty() || s.equals("0");
        return false;
    }

    public List<Weapon> getWeaponsByProfile(Profile profile) {
        return weaponRepository.findByProfile(profile);
    }

    public int updateUnitFields(List<Map<String, Object>> updates) {
        int count = 0;
        for (Map<String, Object> u : updates) {
            Long id = (long) toInt(u.get("id"));
            String field = u.get("field").toString();
            Object rawValue = u.get("value");
            Unit unit = unitRepository.findById(id).orElse(null);
            if (unit == null) continue;

            switch (field) {
                case "cost" -> unit.setCost(toInt(rawValue));
                case "health" -> unit.setHealth(toInt(rawValue));
                case "speed" -> unit.setSpeed(toInt(rawValue));
                case "revealsShroudRange" -> unit.setRevealsShroudRange(rawValue.toString());
                default -> { continue; }
            }
            unitRepository.save(unit);
            count++;
        }
        return count;
    }

    /** Regenerate YAML from current unit data and update stored YamlFiles. */
    public String regenerateYaml(Profile profile, Set<Long> unitIds) {
        List<Unit> units = unitRepository.findByProfile(profile);
        if (units.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (Unit u : units) {
            if (unitIds != null && !unitIds.isEmpty() && !unitIds.contains(u.getId())) continue;
            sb.append(u.getName()).append(":\n");
            sb.append("\tHealth:\n");
            sb.append("\t\tHP: ").append(u.getHealth() * 100).append("\n");
            sb.append("\tArmor:\n");
            sb.append("\t\tType: ").append(u.getArmortype() != null ? u.getArmortype() : "").append("\n");
            sb.append("\tMobile:\n");
            sb.append("\t\tSpeed: ").append(u.getSpeed()).append("\n");
            sb.append("\tValued:\n");
            sb.append("\t\tCost: ").append(u.getCost()).append("\n");
            String range = u.getRevealsShroudRange();
            if (range != null && !range.isEmpty()) {
                sb.append("\tRevealsShroud:\n");
                sb.append("\t\tRange: ").append(range).append("\n");
            }
            sb.append("\n");
        }

        String newYaml = sb.toString();
        // Update all YamlFiles in this profile with the regenerated content
        List<YamlFile> files = yamlFileRepository.findByProfile(profile);
        if (!files.isEmpty()) {
            YamlFile first = files.get(0);
            first.setContent(newYaml);
            first.setFileName("exported_units.yaml");
            yamlFileRepository.save(first);
            // Remove extra files
            for (int i = 1; i < files.size(); i++) {
                yamlFileRepository.delete(files.get(i));
            }
        } else {
            yamlFileRepository.save(new YamlFile("exported_units.yaml", newYaml, profile));
        }
        return newYaml;
    }

    public int updateWeaponFields(List<Map<String, Object>> updates) {
        int count = 0;
        for (Map<String, Object> u : updates) {
            Long id = (long) toInt(u.get("id"));
            String field = u.get("field").toString();
            Object rawValue = u.get("value");
            Weapon w = weaponRepository.findById(id).orElse(null);
            if (w == null) continue;

            switch (field) {
                case "reloadDelay" -> w.setReloadDelay(toInt(rawValue));
                case "range" -> w.setRange(rawValue.toString());
                case "burst" -> w.setBurst(toInt(rawValue));
                case "projectileSpeed" -> w.setProjectileSpeed(toInt(rawValue));
                case "launchAngle" -> w.setLaunchAngle(toInt(rawValue));
                case "inaccuracy" -> w.setInaccuracy(rawValue.toString());
                case "contrailLength" -> w.setContrailLength(toInt(rawValue));
                case "spread" -> w.setSpread(toInt(rawValue));
                case "damage" -> w.setDamage(toInt(rawValue));
                case "versusNone" -> w.setVersusNone(toInt(rawValue));
                case "versusWood" -> w.setVersusWood(toInt(rawValue));
                case "versusLight" -> w.setVersusLight(toInt(rawValue));
                case "versusHeavy" -> w.setVersusHeavy(toInt(rawValue));
                case "versusConcrete" -> w.setVersusConcrete(toInt(rawValue));
                default -> { continue; }
            }
            weaponRepository.save(w);
            count++;
        }
        return count;
    }

    public String regenerateWeaponYaml(Profile profile, Set<Long> weaponIds) {
        List<Weapon> weapons = weaponRepository.findByProfile(profile);
        if (weapons.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (Weapon w : weapons) {
            if (weaponIds != null && !weaponIds.isEmpty() && !weaponIds.contains(w.getId())) continue;
            sb.append(w.getName()).append(":\n");
            if (w.getReloadDelay() > 0) sb.append("\tReloadDelay: ").append(w.getReloadDelay()).append("\n");
            if (w.getRange() != null && !w.getRange().isEmpty()) sb.append("\tRange: ").append(w.getRange()).append("\n");
            if (w.getBurst() > 0) sb.append("\tBurst: ").append(w.getBurst()).append("\n");
            sb.append("\tProjectile:\n");
            if (w.getProjectileSpeed() > 0) sb.append("\t\tSpeed: ").append(w.getProjectileSpeed()).append("\n");
            if (w.getLaunchAngle() > 0) sb.append("\t\tLaunchAngle: ").append(w.getLaunchAngle()).append("\n");
            if (w.getInaccuracy() != null && !w.getInaccuracy().isEmpty()) sb.append("\t\tInaccuracy: ").append(w.getInaccuracy()).append("\n");
            if (w.getContrailLength() > 0) sb.append("\t\tContrailLength: ").append(w.getContrailLength()).append("\n");
            sb.append("\tWarhead@1Dam:\n");
            if (w.getSpread() > 0) sb.append("\t\tSpread: ").append(w.getSpread()).append("\n");
            if (w.getDamage() != 0) sb.append("\t\tDamage: ").append(w.getDamage()).append("\n");
            sb.append("\t\tVersus:\n");
            sb.append("\t\t\tNone: ").append(w.getVersusNone()).append("\n");
            sb.append("\t\t\tWood: ").append(w.getVersusWood()).append("\n");
            sb.append("\t\t\tLight: ").append(w.getVersusLight()).append("\n");
            sb.append("\t\t\tHeavy: ").append(w.getVersusHeavy()).append("\n");
            sb.append("\t\t\tConcrete: ").append(w.getVersusConcrete()).append("\n");
            sb.append("\n");
        }

        String newYaml = sb.toString();
        // Save as a separate weapon YAML file
        List<YamlFile> files = yamlFileRepository.findByProfile(profile);
        yamlFileRepository.save(new YamlFile("exported_weapons.yaml", newYaml, profile));
        return newYaml;
    }

    private String getStringValue(Map<?, ?> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }

    private int getIntValue(Map<?, ?> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }



    // --- YAML sanitization ---

    private String sanitizeYaml(String yaml) {
        yaml = yaml.replace("\t", "    ");
        String[] lines = yaml.split("\n", -1);
        int prevIndent = 0;
        boolean prevIsScalar = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int indent = countLeadingSpaces(line);
            String trimmed = line.substring(indent);
            if (trimmed.isEmpty()) { lines[i] = ""; continue; }

            int colonIdx = trimmed.indexOf(": ");
            boolean isScalar = colonIdx > 0 && !trimmed.substring(colonIdx + 2).trim().isEmpty();

            // If previous line was scalar and current is deeper:
            // the previous line is actually a parent with children -> drop its value
            if (prevIsScalar && indent > prevIndent && isKeyLine(trimmed)) {
                // Revert the previous line: remove the scalar value
                String prevLine = lines[i - 1];
                int prevCi = prevLine.indexOf(": ");
                if (prevCi > 0) {
                    lines[i - 1] = prevLine.substring(0, prevCi + 1);
                }
                // Don't un-indent current line — it's a legitimate child
            } else if (prevIsScalar && indent > prevIndent && !isKeyLine(trimmed)) {
                // Over-indented non-key line: fix indentation (e.g. Radius: 469 under Type: Circle)
                indent = prevIndent;
                line = " ".repeat(indent) + trimmed;
            }

            // Quote values with YAML special characters
            if (colonIdx > 0) {
                String key = trimmed.substring(0, colonIdx);
                String value = trimmed.substring(colonIdx + 2);
                if (needsQuoting(value)) {
                    value = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
                    line = " ".repeat(indent) + key + ": " + value;
                }
            }

            prevIsScalar = isScalar;
            prevIndent = indent;
            lines[i] = line;
        }
        return String.join("\n", lines);
    }

    private boolean isKeyLine(String trimmed) {
        return trimmed.indexOf(": ") > 0 || trimmed.endsWith(":");
    }

    private int countLeadingSpaces(String s) {
        int n = 0;
        while (n < s.length() && s.charAt(n) == ' ') n++;
        return n;
    }

    private boolean isScalarValue(String trimmed) {
        int ci = trimmed.indexOf(": ");
        if (ci <= 0) return false;
        return !trimmed.substring(ci + 2).trim().isEmpty();
    }

    private boolean needsQuoting(String value) {
        if (value.isEmpty() || value.startsWith("\"") || value.startsWith("'")) return false;
        for (char c : value.toCharArray()) {
            if (c == '&' || c == '*' || c == '!' || c == '{' || c == '}' ||
                c == '[' || c == ']' || c == '|' || c == '>' || c == '%' ||
                c == '@' || c == '`' || c == '#' || c == ',') return true;
        }
        return false;
    }
}
