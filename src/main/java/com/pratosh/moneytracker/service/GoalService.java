package com.pratosh.moneytracker.service;

import com.pratosh.moneytracker.domain.AppUser;
import com.pratosh.moneytracker.domain.Goal;
import com.pratosh.moneytracker.dto.Dtos.GoalDto;
import com.pratosh.moneytracker.dto.Dtos.GoalReq;
import com.pratosh.moneytracker.repo.GoalRepository;
import com.pratosh.moneytracker.util.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoalService {
    private final GoalRepository goals;

    static GoalDto toDto(Goal g) {
        return new GoalDto(g.getId(), g.getName(), g.getTargetAmount(), g.getSavedAmount(), g.getTargetDate(),
                Math.min(100, Analytics.percent(g.getSavedAmount(), g.getTargetAmount())));
    }

    @Transactional(readOnly = true)
    public List<GoalDto> list(AppUser u) {
        return goals.findByOwnerIdOrderByIdAsc(u.getId()).stream().map(GoalService::toDto).toList();
    }

    @Transactional
    public GoalDto create(AppUser u, GoalReq r) {
        Goal g = new Goal();
        g.setOwnerId(u.getId());
        apply(g, r);
        return toDto(goals.save(g));
    }

    @Transactional
    public GoalDto update(AppUser u, Long id, GoalReq r) {
        Goal g = goals.findByIdAndOwnerId(id, u.getId()).orElseThrow(() -> ApiException.notFound("Goal"));
        apply(g, r);
        return toDto(goals.save(g));
    }

    @Transactional
    public void delete(AppUser u, Long id) {
        goals.delete(goals.findByIdAndOwnerId(id, u.getId()).orElseThrow(() -> ApiException.notFound("Goal")));
    }

    private void apply(Goal g, GoalReq r) {
        if (r.name().isBlank()) throw ApiException.bad("Please enter a goal name.");
        g.setName(r.name().trim());
        g.setTargetAmount(r.targetAmount());
        g.setSavedAmount(r.savedAmount() == null ? BigDecimal.ZERO : r.savedAmount());
        g.setTargetDate(r.targetDate());
    }
}
