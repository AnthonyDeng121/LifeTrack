package com.lifetrack.controller;

import com.lifetrack.common.Result;
import com.lifetrack.entity.Activity;
import com.lifetrack.service.ActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    @Autowired
    private ActivityService activityService;

    @GetMapping
    public Result<List<Activity>> getAll() {
        return Result.success(activityService.findAll());
    }

    @PostMapping
    public Result<Activity> create(@RequestBody Activity activity) {
        return Result.success(activityService.save(activity));
    }

    @GetMapping("/{id}")
    public Result<Activity> getById(@PathVariable Long id) {
        return Result.success(activityService.findById(id));
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        activityService.deleteById(id);
        return Result.success("Deleted successfully");
    }
}
