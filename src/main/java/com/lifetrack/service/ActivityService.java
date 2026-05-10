package com.lifetrack.service;

import com.lifetrack.entity.Activity;
import java.util.List;

public interface ActivityService {
    List<Activity> findAll();
    Activity save(Activity activity);
    Activity findById(Long id);
    void deleteById(Long id);
}
