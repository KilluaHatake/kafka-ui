import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BASE_PARAMS } from 'lib/constants';
import { showAlert } from 'lib/errorHandling';
import { clusterApprovalsPath } from 'lib/paths';

export type ApprovalStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'EXECUTED'
  | 'EXECUTION_FAILED'
  | 'CANCELLED';

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

export interface ApprovalPermissions {
  username: string;
  roles: string[];
  canApprove: boolean;
  canViewApprovals?: boolean;
  canManageWorkflowSettings?: boolean;
  canAccessAlertSettings?: boolean;
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

  getPermissions: async (clusterName: string): Promise<ApprovalPermissions> => {
    const res = await fetch(
      `${getBaseUrl()}/api/clusters/${encodeURIComponent(clusterName)}/approvals/permissions`,
      {
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
      }
    );
    if (!res.ok) {
      return {
        username: '',
        roles: [],
        canApprove: true,
        canViewApprovals: true,
        canManageWorkflowSettings: true,
        canAccessAlertSettings: true,
      };
    }
    return res.json();
  },

  deleteApproval: async (clusterName: string, id: string): Promise<void> => {
    const res = await fetch(
      `${getBaseUrl()}/api/clusters/${encodeURIComponent(clusterName)}/approvals/${encodeURIComponent(id)}`,
      {
        method: 'DELETE',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
      }
    );
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || `Failed to delete approval request: ${res.statusText}`);
    }
  },
};

export function useApprovalPermissions(clusterName: string) {
  return useQuery({
    queryKey: ['clusters', clusterName, 'approvals', 'permissions'],
    queryFn: () => approvalsApi.getPermissions(clusterName),
    staleTime: 30000,
  });
}

export function useDeleteApproval(clusterName: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => approvalsApi.deleteApproval(clusterName, id),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['clusters', clusterName, 'approvals'],
      });
    },
  });
}

export const isApprovalResponse = (res: any): boolean => {
  if (!res) return false;
  if (res.status === 202 || res.code === 4022) return true;
  if (
    res.status !== 403 &&
    res.status !== 401 &&
    res.status !== 404 &&
    res.status !== 500 &&
    typeof res.message === 'string' &&
    (res.message.toLowerCase().includes('submitted for approval') ||
      res.message.toLowerCase().includes('approval required'))
  ) {
    return true;
  }
  return false;
};

export const handleApprovalRedirect = (
  resOrError: any,
  clusterName: string,
  navigate: (path: string) => void,
  defaultMessage = 'Request submitted to approval queue.'
): boolean => {
  if (isApprovalResponse(resOrError)) {
    showAlert('warning', {
      title: 'Sent for Approval',
      message: resOrError?.message || defaultMessage,
    });
    navigate(clusterApprovalsPath(clusterName));
    return true;
  }
  return false;
};

export function useApprovals(
  clusterName: string,
  status?: ApprovalStatus,
  type?: ApprovalTargetType,
  enabled: boolean = true
) {
  return useQuery({
    queryKey: ['clusters', clusterName, 'approvals', { status, type }],
    queryFn: () => approvalsApi.getApprovals(clusterName, status, type),
    refetchInterval: enabled ? 5000 : false,
    enabled,
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

export function useApprovalSettings(enabled: boolean = true) {
  return useQuery({
    queryKey: ['admin', 'approvalSettings'],
    queryFn: () => approvalsApi.getSettings(),
    enabled,
    retry: false,
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
