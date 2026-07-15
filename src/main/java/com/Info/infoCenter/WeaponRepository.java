package com.Info.infoCenter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WeaponRepository extends JpaRepository<Weapon, Long> {
    List<Weapon> findByProfile(Profile profile);
    void deleteByProfile(Profile profile);
}
