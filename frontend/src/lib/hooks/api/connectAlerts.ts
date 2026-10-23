import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BASE_PARAMS } from 'lib/constants';

export interface SlackAlertSettings {
  enabled: boolean;
  webhookUrl?: string;
  channel?: string;
}

export interface EmailAlertSettings {
  enabled: boolean;
  smtpHost?: string;
  smtpPort?: number;
  username?: string;
  password?: string;
  starttlsEnabled?: boolean;
  sslEnabled?: boolean;
  from?: string;
  recipients: string[];
}

export interface ConnectAlertSettings {
  enabled: boolean;
  pollingIntervalMs: number;
  throttleIntervalMinutes: number;
  slack: SlackAlertSettings;
  email: EmailAlertSettings;
}

export interface TestAlertResponse {
  success: boolean;
  message: string;
}

const getBaseUrl = () => BASE_PARAMS.basePath || '';

export const connectAlertsApi = {
  getSettings: async (): Promise<ConnectAlertSettings> => {
    const res = await fetch(`${getBaseUrl()}/api/connect-alerts/settings`, {
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
    });
    if (!res.ok) {
      throw new Error(`Failed to fetch alert settings: ${res.statusText}`);
    }
    return res.json();
  },

  updateSettings: async (
    settings: ConnectAlertSettings
  ): Promise<ConnectAlertSettings> => {
    const res = await fetch(`${getBaseUrl()}/api/connect-alerts/settings`, {
      method: 'PUT',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(settings),
    });
    if (!res.ok) {
      throw new Error(`Failed to update alert settings: ${res.statusText}`);
    }
    return res.json();
  },

  sendTestAlert: async (
    channel: 'slack' | 'email' | 'all'
  ): Promise<TestAlertResponse> => {
    const res = await fetch(`${getBaseUrl()}/api/connect-alerts/test`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ channel }),
    });
    if (!res.ok) {
      throw new Error(`Failed to send test alert: ${res.statusText}`);
    }
    return res.json();
  },
};

export const useConnectAlertSettings = () => {
  return useQuery({
    queryKey: ['connectAlertSettings'],
    queryFn: connectAlertsApi.getSettings,
  });
};

export const useUpdateConnectAlertSettings = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: connectAlertsApi.updateSettings,
    onSuccess: (updated) => {
      queryClient.setQueryData(['connectAlertSettings'], updated);
    },
  });
};

export const useSendTestAlert = () => {
  return useMutation({
    mutationFn: (channel: 'slack' | 'email' | 'all') =>
      connectAlertsApi.sendTestAlert(channel),
  });
};
