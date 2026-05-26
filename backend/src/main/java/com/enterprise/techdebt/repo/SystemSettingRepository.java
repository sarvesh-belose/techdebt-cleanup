package com.enterprise.techdebt.repo;

import com.enterprise.techdebt.domain.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
}
