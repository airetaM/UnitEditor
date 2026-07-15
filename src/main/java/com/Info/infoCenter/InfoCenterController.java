package com.Info.infoCenter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class InfoCenterController {

    @Autowired
    private UnitService unitService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private YamlFileRepository yamlFileRepository;

    // -- Page routes --

    @GetMapping("/")
    public String home() {
        return "redirect:/profiles";
    }

    @GetMapping("/profiles")
    public String profiles(Model model) {
        model.addAttribute("profiles", profileRepository.findAll());
        return "profiles";
    }

    @GetMapping("/profiles/{id}")
    public String profileDetail(@PathVariable Long id, Model model) {
        Profile profile = profileRepository.findById(id).orElseThrow();
        List<Unit> units = unitService.getUnitsByProfile(profile);
        computeDamage(units, profile);
        model.addAttribute("profile", profile);
        model.addAttribute("units", units);
        return "units";
    }

    // -- Profile API --

    @PostMapping("/api/profiles")
    @ResponseBody
    public String createProfile(@RequestParam String name) {
        if (name.isBlank()) return jsonResponse(false, "Name must not be empty.");
        profileRepository.save(new Profile(name.trim()));
        return jsonResponse(true, "Profile created.");
    }

    @DeleteMapping("/api/profiles/{id}")
    @ResponseBody
    public String deleteProfile(@PathVariable Long id) {
        Profile profile = profileRepository.findById(id).orElseThrow();
        unitService.clearUnitsByProfile(profile);
        profileRepository.delete(profile);
        return jsonResponse(true, "Profile deleted.");
    }

    // -- Upload with inheritance support --

    @PostMapping("/api/profiles/{id}/upload")
    @ResponseBody
    public String uploadYaml(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return jsonResponse(false, "File is empty.");
        try {
            Profile profile = profileRepository.findById(id).orElseThrow();
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            int count = unitService.parseAndAddUnits(content, file.getOriginalFilename(), profile);
            return jsonResponse(true, count + " units loaded.");
        } catch (IOException e) {
            return jsonResponse(false, "Error reading file: " + e.getMessage());
        } catch (Exception e) {
            return jsonResponse(false, "Error: " + e.getMessage());
        }
    }

    @GetMapping("/api/profiles/{id}/yaml-files")
    @ResponseBody
    public List<Map<String, Object>> listYamlFiles(@PathVariable Long id) {
        Profile profile = profileRepository.findById(id).orElseThrow();
        return yamlFileRepository.findByProfile(profile).stream().map(f -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", f.getId());
            m.put("fileName", f.getFileName());
            return m;
        }).toList();
    }

    @DeleteMapping("/api/profiles/{id}/yaml-files/{fileId}")
    @ResponseBody
    public String deleteYamlFile(@PathVariable Long id, @PathVariable Long fileId) {
        yamlFileRepository.deleteById(fileId);
        return jsonResponse(true, "File deleted.");
    }

    @PostMapping("/api/profiles/{id}/clear")
    @ResponseBody
    public String clearUnits(@PathVariable Long id) {
        try {
            Profile profile = profileRepository.findById(id).orElseThrow();
            unitService.clearUnitsByProfile(profile);
            return jsonResponse(true, "All units deleted.");
        } catch (Exception e) {
            return jsonResponse(false, "Error: " + e.getMessage());
        }
    }

    @GetMapping("/api/profiles/{id}/units")
    @ResponseBody
    public List<Unit> getUnits(@PathVariable Long id) {
        return profileRepository.findById(id)
                .map(profile -> {
                    List<Unit> units = unitService.getUnitsByProfile(profile);
                    computeDamage(units, profile);
                    return units;
                })
                .orElse(List.of());
    }

    @GetMapping("/api/profiles/{id}/weapons")
    @ResponseBody
    public List<Weapon> getWeapons(@PathVariable Long id) {
        return profileRepository.findById(id)
                .map(unitService::getWeaponsByProfile)
                .orElse(List.of());
    }

    @PutMapping("/api/profiles/{id}/units")
    @ResponseBody
    public String updateUnits(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> unitUpdates = (List<Map<String, Object>>) payload.get("unitUpdates");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> weaponUpdates = (List<Map<String, Object>>) payload.get("weaponUpdates");

            int count = 0;
            if (unitUpdates != null && !unitUpdates.isEmpty())
                count += unitService.updateUnitFields(unitUpdates);
            if (weaponUpdates != null && !weaponUpdates.isEmpty())
                count += unitService.updateWeaponFields(weaponUpdates);

            Profile profile = profileRepository.findById(id).orElseThrow();
            if (unitUpdates != null && !unitUpdates.isEmpty())
                unitService.regenerateYaml(profile, null);
            if (weaponUpdates != null && !weaponUpdates.isEmpty())
                unitService.regenerateWeaponYaml(profile, null);

            return jsonResponse(true, count + " fields updated.");
        } catch (Exception e) {
            return jsonResponse(false, "Error: " + e.getMessage());
        }
    }

    @GetMapping("/api/profiles/{id}/download")
    @ResponseBody
    public ResponseEntity<byte[]> downloadYaml(@PathVariable Long id,
            @RequestParam(required = false) List<Long> unitIds) {
        Profile profile = profileRepository.findById(id).orElseThrow();
        Set<Long> ids = unitIds != null ? new java.util.HashSet<>(unitIds) : null;
        String yaml = unitService.regenerateYaml(profile, ids);
        if (yaml == null) return ResponseEntity.notFound().build();
        byte[] bytes = yaml.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"exported_units.yaml\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    @GetMapping("/api/profiles/{id}/download-weapons")
    @ResponseBody
    public ResponseEntity<byte[]> downloadWeaponsYaml(@PathVariable Long id,
            @RequestParam(required = false) List<Long> weaponIds) {
        Profile profile = profileRepository.findById(id).orElseThrow();
        Set<Long> ids = weaponIds != null ? new java.util.HashSet<>(weaponIds) : null;
        String yaml = unitService.regenerateWeaponYaml(profile, ids);
        if (yaml == null) return ResponseEntity.notFound().build();
        byte[] bytes = yaml.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"exported_weapons.yaml\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    private void computeDamage(List<Unit> units, Profile profile) {
        List<Weapon> weapons = unitService.getWeaponsByProfile(profile);
        java.util.Map<String, Integer> weaponDamage = new java.util.HashMap<>();
        for (Weapon w : weapons) {
            weaponDamage.put(w.getName(), w.getDamage());
        }
        for (Unit u : units) {
            int maxDmg = u.getWeapons().stream()
                    .mapToInt(wn -> weaponDamage.getOrDefault(wn, 0))
                    .max().orElse(0);
            u.setDamage(maxDmg);
        }
    }

    private String jsonResponse(boolean success, String message) {
        return "{\"success\": " + success + ", \"message\": \"" + escapeJson(message) + "\"}";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
