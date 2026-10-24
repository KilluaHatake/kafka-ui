package io.kafbat.ui.controller;

import io.kafbat.ui.config.ApprovalProperties;
import io.kafbat.ui.config.auth.AuthenticatedUser;
import io.kafbat.ui.model.approval.ApprovalRequest;
import io.kafbat.ui.model.approval.ApprovalSettingsDto;
import io.kafbat.ui.model.approval.ApprovalStatus;
import io.kafbat.ui.model.approval.ApprovalTargetType;
import io.kafbat.ui.service.approval.ApprovalService;
import io.kafbat.ui.service.rbac.AccessControlService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ApprovalController extends AbstractController {

  private final ApprovalService approvalService;
  private final ApprovalProperties approvalProperties;

  @GetMapping("/api/clusters/{clusterName}/approvals")
  public Mono<ResponseEntity<List<ApprovalRequest>>> getApprovals(
      @PathVariable String clusterName,
      @RequestParam(required = false) ApprovalStatus status,
      @RequestParam(required = false) ApprovalTargetType type
  ) {
    getCluster(clusterName);
    return AccessControlService.getUser()
        .switchIfEmpty(Mono.just(new AuthenticatedUser("system", java.util.List.of("Architect"))))
        .flatMap(user -> {
          if (!approvalService.canAccessApprovals(clusterName, user)) {
            return Mono.error(new AccessDeniedException("User is not authorized to view approval requests"));
          }
          return approvalService.getRequests(clusterName, status, type)
              .collectList()
              .map(ResponseEntity::ok);
        });
  }

  @GetMapping("/api/clusters/{clusterName}/approvals/{id}")
  public Mono<ResponseEntity<ApprovalRequest>> getApproval(
      @PathVariable String clusterName,
      @PathVariable String id
  ) {
    getCluster(clusterName);
    return AccessControlService.getUser()
        .switchIfEmpty(Mono.just(new AuthenticatedUser("system", java.util.List.of("Architect"))))
        .flatMap(user -> {
          if (!approvalService.canAccessApprovals(clusterName, user)) {
            return Mono.error(new AccessDeniedException("User is not authorized to view approval requests"));
          }
          return approvalService.getRequest(clusterName, id)
              .map(ResponseEntity::ok);
        });
  }

  @PostMapping("/api/clusters/{clusterName}/approvals/{id}/approve")
  public Mono<ResponseEntity<ApprovalRequest>> approve(
      @PathVariable String clusterName,
      @PathVariable String id
  ) {
    var cluster = getCluster(clusterName);
    return AccessControlService.getUser()
        .switchIfEmpty(Mono.just(new AuthenticatedUser("system", java.util.List.of("Architect"))))
        .flatMap(user -> approvalService.approveRequest(cluster, id, user))
        .map(ResponseEntity::ok);
  }

  @PostMapping("/api/clusters/{clusterName}/approvals/{id}/reject")
  public Mono<ResponseEntity<ApprovalRequest>> reject(
      @PathVariable String clusterName,
      @PathVariable String id,
      @RequestBody(required = false) Map<String, String> body
  ) {
    var cluster = getCluster(clusterName);
    String reason = body != null ? body.get("reason") : null;
    return AccessControlService.getUser()
        .switchIfEmpty(Mono.just(new AuthenticatedUser("system", java.util.List.of("Architect"))))
        .flatMap(user -> approvalService.rejectRequest(cluster, id, user, reason))
        .map(ResponseEntity::ok);
  }

  @GetMapping("/api/clusters/{clusterName}/approvals/permissions")
  public Mono<ResponseEntity<Map<String, Object>>> getPermissions(@PathVariable String clusterName) {
    return AccessControlService.getUser()
        .switchIfEmpty(Mono.just(new AuthenticatedUser("system", java.util.List.of("Architect"))))
        .map(user -> {
          Map<String, Object> perms = new java.util.HashMap<>();
          perms.put("username", user.principal());
          perms.put("roles", user.groups() != null ? user.groups() : java.util.List.of());
          perms.put("canApprove", approvalService.isApprover(user));
          perms.put("canViewApprovals", approvalService.canAccessApprovals(clusterName, user));
          perms.put("canManageWorkflowSettings", approvalService.canManageWorkflowSettings(user));
          perms.put("canAccessAlertSettings", approvalService.canAccessAlertSettings(user, clusterName));
          return ResponseEntity.ok(perms);
        });
  }

  @DeleteMapping("/api/clusters/{clusterName}/approvals/{id}")
  public Mono<ResponseEntity<Void>> deleteApproval(
      @PathVariable String clusterName,
      @PathVariable String id
  ) {
    var cluster = getCluster(clusterName);
    return AccessControlService.getUser()
        .switchIfEmpty(Mono.just(new AuthenticatedUser("system", java.util.List.of("Architect"))))
        .flatMap(user -> approvalService.deleteRequest(cluster, id, user))
        .thenReturn(ResponseEntity.noContent().build());
  }

  @GetMapping("/api/admin/approval-settings")
  public Mono<ResponseEntity<ApprovalSettingsDto>> getApprovalSettings() {
    return AccessControlService.getUser()
        .switchIfEmpty(Mono.just(new AuthenticatedUser("system", java.util.List.of("Architect"))))
        .flatMap(user -> {
          if (!approvalService.canManageWorkflowSettings(user)) {
            return Mono.error(new AccessDeniedException("User is not authorized to view approval settings"));
          }
          return Mono.just(ResponseEntity.ok(ApprovalSettingsDto.fromProperties(approvalProperties)));
        });
  }

  @PutMapping("/api/admin/approval-settings")
  public Mono<ResponseEntity<ApprovalSettingsDto>> updateApprovalSettings(
      @RequestBody ApprovalSettingsDto newSettings
  ) {
    return AccessControlService.getUser()
        .switchIfEmpty(Mono.just(new AuthenticatedUser("system", java.util.List.of("Architect"))))
        .flatMap(user -> {
          if (!approvalService.canManageWorkflowSettings(user)) {
            return Mono.error(new AccessDeniedException("User is not authorized to modify approval settings"));
          }
          if (newSettings != null) {
            approvalProperties.setEnabled(newSettings.isEnabled());
            approvalProperties.setRequireTopicCreation(newSettings.isRequireTopicCreation());
            approvalProperties.setRequireTopicConfigUpdates(newSettings.isRequireTopicConfigUpdates());
            approvalProperties.setRequireConnectorDeployments(newSettings.isRequireConnectorDeployments());
            approvalProperties.setRequireBrokerConfigUpdates(newSettings.isRequireBrokerConfigUpdates());
            approvalProperties.setRequireAlertSettingsUpdates(newSettings.isRequireAlertSettingsUpdates());
            approvalProperties.setRequireConsumerGroupDelete(newSettings.isRequireConsumerGroupDelete());
            approvalProperties.setRequireConnectorResetOffsets(newSettings.isRequireConnectorResetOffsets());
            if (newSettings.getDirectApplyRoles() != null) {
              approvalProperties.setDirectApplyRoles(newSettings.getDirectApplyRoles());
            }
            if (newSettings.getApproverRoles() != null) {
              approvalProperties.setApproverRoles(newSettings.getApproverRoles());
            }
          }
          return Mono.just(ResponseEntity.ok(ApprovalSettingsDto.fromProperties(approvalProperties)));
        });
  }
}
