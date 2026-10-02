package dev.aliencode.adapters.mission.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.aliencode.adapters.mission.web.mapper.DeliveryWebMapper;
import dev.aliencode.adapters.mission.web.response.DeliveryResponse;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.usecase.ApproveDeliveryUseCase;
import dev.aliencode.core.mission.usecase.GetDeliveryUseCase;
import dev.aliencode.core.mission.usecase.RejectDeliveryUseCase;

/** A entrega da missão: ver o patch e decidir (aplicar numa branch local ou descartar). */
@RestController
@RequestMapping("/api/missions/{id}/delivery")
public class DeliveryController {

    private final GetDeliveryUseCase get;
    private final ApproveDeliveryUseCase approve;
    private final RejectDeliveryUseCase reject;

    private final DeliveryWebMapper mapper;

    public DeliveryController(
            GetDeliveryUseCase get,
            ApproveDeliveryUseCase approve,
            RejectDeliveryUseCase reject,
            DeliveryWebMapper mapper
    ) {
        this.get = get;
        this.approve = approve;
        this.reject = reject;
        this.mapper = mapper;
    }

    @GetMapping
    public DeliveryResponse get(@PathVariable String id) {
        return this.mapper.toResponse(
                this.get.get(new MissionId(id))
        );
    }

    @PostMapping("/approve")
    public DeliveryResponse approve(@PathVariable String id) {
        return this.mapper.toResponse(
                this.approve.approve(new MissionId(id))
        );
    }

    @PostMapping("/reject")
    public DeliveryResponse reject(@PathVariable String id) {
        return this.mapper.toResponse(
                this.reject.reject(new MissionId(id))
        );
    }
}
