package com.Info.infoCenter;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface YamlFileRepository extends JpaRepository<YamlFile, Long> {
    List<YamlFile> findByProfile(Profile profile);
}
