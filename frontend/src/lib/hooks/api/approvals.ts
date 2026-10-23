import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BASE_PARAMS } from 'lib/constants';

export type ApprovalStatus =
  | 'PENDING'
  | 'EXECUTED'
  | 'REJECTED'
  | 'EXECUTION_FAILED';

export type ApprovalTargetType =
  | 'TOPIC_CREATE'
  | 'TOPIC_CONFIG_UPDATE'
  | 'CONNECTOR_CREATE'
  | 'CONNECTOR_UPDATE'
  | 'CONNECTOR_ACTION'
  | 'BROKER_CONFIG_UPDATE'
  | 'ALERT_SETTINGS_UPDATE'
  | 'CONSUMER_GROUP_DELETE'
  | 'CONNECTOR_RESET_OFFSETS';

export interface ApprovalRequest {
  id: string;
  clusterName: string;
  type: ApprovalTargetType;
  targetName: string;
  connectName?: string;
  requesterEmail: string;
  requesterRoles: string[];
  status: ApprovalStatus;
  createdAt: string;
  updatedAt: string;
  approverEmail?: string;
  rejectionReason?: string;
  executionError?: string;
  oldConfig?: Record<string, any>;
  newConfig?: Record<string, any>;
  metadata?: Record<string, any>;
}

export interface ApprovalProperties {
  enabled: boolean;
  topic: string;
  requireTopicCreation: boolean;
  requireTopicConfigUpdates: boolean;
  requireConnectorDeployments: boolean;
  requireBrokerConfigUpdates: boolean;
  requireAlertSettingsUpdates: boolean;
  requireConsumerGroupDelete: boolean;
  requireConnectorResetOffsets: boolean;
  approverRoles: string[];
  directApplyRoles: string[];
}

const getBaseUrl = () => BASE_PARAMS.basePath || '';

export const approvalsApi = {
  getApprovals: async (
    clusterName: string,
    status?: ApprovalStatus,
    type?: ApprovalTargetType
  ): Promise<ApprovalRequest[]> => {
    const params = new URLSearchParams();
    if (status) params.append('status', status);
    if (type) params.append('type', type);
    const qs = params.toString() ? `?${params.toString()}` : '';

    const res = await fetch(
      `${getBaseUrl()}/api/clusters/${encodeURIComponent(clusterName)}/approvals${qs}`,
      {
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
      }
    );
    if (!res.ok) {
      throw new Error(`Failed to fetch approvals: ${res.statusText}`);
    }
    return res.json();
  },

  getApproval: async (
    clusterName: string,
    id: string
  ): Promise<ApprovalRequest> => {
    const res = await fetch(
      `${getBaseUrl()}/api/clusters/${encodeURIComponent(clusterName)}/approvals/${encodeURIComponent(id)}`,
      {
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
      }
    );
    if (!res.ok) {
      throw new Error(`Failed to fetch approval: ${res.statusText}`);
    }
    return res.json();
  },

  approveRequest: async (
    clusterName: string,
    id: string
  ): Promise<ApprovalRequest> => {
    const res = await fetch(
      `${getBaseUrl()}/api/clusters/${encodeURIComponent(clusterName)}/approvals/${encodeURIComponent(id)}/approve`,
      {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
      }
    );
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || `Failed to approve request: ${res.statusText}`);
    }
    return res.json();
  },

  rejectRequest: async (
    clusterName: string,
    id: string,
    reason?: string
  ): Promise<ApprovalRequest> => {
    const res = await fetch(
      `${getBaseUrl()}/api/clusters/${encodeURIComponent(clusterName)}/approvals/${encodeURIComponent(id)}/reject`,
      {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason }),
      }
    );
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || `Failed to reject request: ${res.statusText}`);
    }
    return res.json();
  },

  getSettings: async (): Promise<ApprovalProperties> => {
    const res = await fetch(`${getBaseUrl()}/api/admin/approval-settings`, {
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
    });
    if (!res.ok) {
      throw new Error(`Failed to fetch approval settings: ${res.statusText}`);
    }
    return res.json();
  },

  updateSettings: async (
    settings: ApprovalProperties
  ): Promise<ApprovalProperties> => {
    const res = await fetch(`${getBaseUrl()}/api/admin/approval-settings`, {
      method: 'PUT',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(settings),
    });
    if (!res.ok) {
      throw new Error(`Failed to update approval settings: ${res.statusText}`);
    }
    return res.json();
  },
};

export function useApprovals(
  clusterName: string,
  status?: ApprovalStatus,
  type?: ApprovalTargetType
) {
  return useQuery({
    queryKey: ['clusters', clusterName, 'approvals', { status, type }],
    queryFn: () => approvalsApi.getApprovals(clusterName, status, type),
    refetchInterval: 5000,
  });
}

export function useApproval(clusterName: string, id: string) {
  return useQuery({
    queryKey: ['clusters', clusterName, 'approvals', id],
    queryFn: () => approvalsApi.getApproval(clusterName, id),
  });
}

export function useApproveRequest(clusterName: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => approvalsApi.approveRequest(clusterName, id),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['clusters', clusterName, 'approvals'],
      });
    },
  });
}

export function useRejectRequest(clusterName: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }: { id: string; reason?: string }) =>
      approvalsApi.rejectRequest(clusterName, id, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['clusters', clusterName, 'approvals'],
      });
    },
  });
}

export function useApprovalSettings() {
  return useQuery({
    queryKey: ['admin', 'approvalSettings'],
    queryFn: () => approvalsApi.getSettings(),
  });
}

export function useUpdateApprovalSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (settings: ApprovalProperties) =>
      approvalsApi.updateSettings(settings),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['admin', 'approvalSettings'],
      });
    },
  });
}
