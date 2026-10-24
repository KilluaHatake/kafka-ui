import React from 'react';
import { render } from 'lib/testHelpers';
import { screen, fireEvent } from '@testing-library/react';
import ConnectAlertsPage from 'components/Connect/Alerts/ConnectAlertsPage';
import * as hooks from 'lib/hooks/api/connectAlerts';

jest.mock('lib/hooks/api/connectAlerts');

const mockSettings: hooks.ConnectAlertSettings = {
  enabled: true,
  pollingIntervalMs: 30000,
  throttleIntervalMinutes: 15,
  slack: {
    enabled: true,
    webhookUrl: 'https://hooks.slack.com/services/T00/B00/XXXX',
    channel: '#kafka-alerts',
  },
  email: {
    enabled: false,
    smtpHost: 'smtp.gmail.com',
    smtpPort: 587,
    username: 'alerts@test.com',
    password: '',
    starttlsEnabled: true,
    sslEnabled: false,
    from: 'kafbat-alerts@test.com',
    recipients: ['team@test.com'],
  },
};

describe('ConnectAlertsPage', () => {
  const mockUpdateMutation = {
    mutate: jest.fn(),
    isPending: false,
  };
  const mockTestAlertMutation = {
    mutate: jest.fn(),
    isPending: false,
  };

  beforeEach(() => {
    jest.clearAllMocks();
    (hooks.useConnectAlertSettings as jest.Mock).mockReturnValue({
      data: mockSettings,
      isLoading: false,
      isError: false,
    });
    (hooks.useUpdateConnectAlertSettings as jest.Mock).mockReturnValue(
      mockUpdateMutation
    );
    (hooks.useSendTestAlert as jest.Mock).mockReturnValue(
      mockTestAlertMutation
    );
  });

  it('renders loading state when isLoading is true', () => {
    (hooks.useConnectAlertSettings as jest.Mock).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
    });

    render(<ConnectAlertsPage />);
    expect(screen.getByText('Loading alert settings...')).toBeInTheDocument();
  });

  it('renders error state when isError is true', () => {
    (hooks.useConnectAlertSettings as jest.Mock).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
    });

    render(<ConnectAlertsPage />);
    expect(
      screen.getByText('Failed to load Kafka Connect alert settings.')
    ).toBeInTheDocument();
  });

  it('renders settings cards and inputs with initial data', () => {
    render(<ConnectAlertsPage />);

    expect(
      screen.getByText('Proactive Connect Error Monitoring')
    ).toBeInTheDocument();
    expect(screen.getByText('Slack Notifications')).toBeInTheDocument();
    expect(screen.getByText('Email (SMTP) Notifications')).toBeInTheDocument();

    expect(screen.getByDisplayValue('30')).toBeInTheDocument(); // 30s
    expect(screen.getByDisplayValue('15')).toBeInTheDocument(); // 15m
    expect(
      screen.getByDisplayValue('https://hooks.slack.com/services/T00/B00/XXXX')
    ).toBeInTheDocument();
    expect(screen.getByDisplayValue('#kafka-alerts')).toBeInTheDocument();
    expect(screen.getByDisplayValue('smtp.gmail.com')).toBeInTheDocument();
  });

  it('calls update mutation when Save Settings is clicked', () => {
    render(<ConnectAlertsPage />);

    const saveButton = screen.getByRole('button', { name: 'Save Settings' });
    fireEvent.click(saveButton);

    expect(mockUpdateMutation.mutate).toHaveBeenCalledTimes(1);
    expect(mockUpdateMutation.mutate).toHaveBeenCalledWith(
      mockSettings,
      expect.any(Object)
    );
  });

  it('calls test alert mutation when Send Test Slack Notification is clicked', () => {
    render(<ConnectAlertsPage />);

    const testSlackBtn = screen.getByRole('button', {
      name: 'Send Test Slack Notification',
    });
    fireEvent.click(testSlackBtn);

    expect(mockTestAlertMutation.mutate).toHaveBeenCalledWith(
      'slack',
      expect.any(Object)
    );
  });

  it('allows erasing polling interval without resetting to 30 or 5 and allows typing 100', () => {
    render(<ConnectAlertsPage />);

    const pollingInput = screen.getByLabelText('Polling Interval (Seconds)');
    expect(pollingInput).toHaveValue(30);

    // User clears the field (backspaces everything)
    fireEvent.change(pollingInput, { target: { value: '' } });
    expect(pollingInput).toHaveValue(null);

    // User types 100
    fireEvent.change(pollingInput, { target: { value: '100' } });
    expect(pollingInput).toHaveValue(100);

    const saveButton = screen.getByRole('button', { name: 'Save Settings' });
    fireEvent.click(saveButton);

    expect(mockUpdateMutation.mutate).toHaveBeenCalledWith(
      expect.objectContaining({
        pollingIntervalMs: 100000,
      }),
      expect.any(Object)
    );
  });

  it('normalizes polling interval on blur when left empty or below 5', () => {
    render(<ConnectAlertsPage />);

    const pollingInput = screen.getByLabelText('Polling Interval (Seconds)');

    // Set to 2 (below min 5) and blur
    fireEvent.change(pollingInput, { target: { value: '2' } });
    fireEvent.blur(pollingInput);
    expect(pollingInput).toHaveValue(5);

    // Clear and blur -> resets to 30 default
    fireEvent.change(pollingInput, { target: { value: '' } });
    fireEvent.blur(pollingInput);
    expect(pollingInput).toHaveValue(30);
  });
});
