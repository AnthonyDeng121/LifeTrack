package com.lifetrack.service.impl;

import com.lifetrack.entity.Activity;
import com.lifetrack.dao.ActivityDao;
import com.lifetrack.service.ActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ActivityServiceImpl implements ActivityService {

    @Autowired
    private ActivityDao activityDao;

    @Override
    public List<Activity> findAll() {
        return activityDao.findAll();
    }

    @Override
    public Activity save(Activity activity) {
        return activityDao.save(activity);
    }

    @Override
    public Activity findById(Long id) {
        return activityDao.findById(id).orElseThrow(() -> new RuntimeException("Activity not found with id: " + id));
    }

    @Override
    public void deleteById(Long id) {
        activityDao.deleteById(id);
    }
}
