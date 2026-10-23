import React, { useState, useEffect } from 'react';
import Switch from 'components/common/Switch/Switch';
import { Button } from 'components/common/Button/Button';
import * as InputStyled from 'components/common/Input/Input.styled';
import {
  useConnectAlertSettings,
  useUpdateConnectAlertSettings,
  useSendTestAlert,
  ConnectAlertSettings,
} from 'lib/hooks/api/connectAlerts';
import toast from 'react-hot-toast';
import * as S from './ConnectAlertsPage.styled';

const DEFAULT_SETTINGS: ConnectAlertSettings = {
  enabled: false,
  pollingIntervalMs: 30000,
  throttleIntervalMinutes: 15,
  slack: {
    enabled: false,
    webhookUrl: '',
    channel: '',
  },
  email: {
    enabled: false,
    smtpHost: '',
    smtpPort: 587,
    username: '',
    password: '',
    starttlsEnabled: true,
    sslEnabled: false,
    from: '',
    recipients: [],
  },
};

const ConnectAlertsPage: React.FC = () => {
  const { data: serverSettings, isLoading, isError } = useConnectAlertSettings();
  const updateMutation = useUpdateConnectAlertSettings();
  const testAlertMutation = useSendTestAlert();

  const [formState, setFormState] = useState<ConnectAlertSettings>(DEFAULT_SETTINGS);
  const [showSlackWebhook, setShowSlackWebhook] = useState(false);
  const [showEmailPassword, setShowEmailPassword] = useState(false);
  const [recipientsInput, setRecipientsInput] = useState('');
  const [testingChannel, setTestingChannel] = useState<'slack' | 'email' | null>(null);

  useEffect(() => {
    if (serverSettings) {
      setFormState(serverSettings);
      setRecipientsInput(serverSettings.email?.recipients?.join(', ') || '');
    }
  }, [serverSettings]);

  const handleRecipientsChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value;
    setRecipientsInput(raw);
    const parsed = raw
      .split(',')
      .map((r) => r.trim())
      .filter((r) => r.length > 0);
    setFormState((prev) => ({
      ...prev,
      email: {
        ...prev.email,
        recipients: parsed,
      },
    }));
  };

  const handleSave = () => {
    updateMutation.mutate(formState, {
      onSuccess: () => {
        toast.success('Connect alert settings saved successfully');
      },
      onError: (err) => {
        toast.error(`Failed to save settings: ${err.message}`);
      },
    });
  };

  const handleTestAlert = (channel: 'slack' | 'email') => {
    setTestingChannel(channel);
    testAlertMutation.mutate(channel, {
      onSuccess: (res) => {
        setTestingChannel(null);
        if (res.success) {
          toast.success(`Test alert sent successfully to ${channel}`);
        } else {
          toast.error(res.message || `Failed to send test ${channel} alert`);
        }
      },
      onError: (err) => {
        setTestingChannel(null);
        toast.error(`Test ${channel} alert failed: ${err.message}`);
      },
    });
  };

  const handleReset = () => {
    if (serverSettings) {
      setFormState(serverSettings);
      setRecipientsInput(serverSettings.email?.recipients?.join(', ') || '');
      toast('Form reset to server settings', { icon: 'ℹ️' });
    }
  };

  if (isLoading) {
    return <S.PageContainer>Loading alert settings...</S.PageContainer>;
  }

  if (isError) {
    return (
      <S.PageContainer>
        <S.Card>
          <S.CardTitle>Error</S.CardTitle>
          <S.CardSubtitle>Failed to load Kafka Connect alert settings.</S.CardSubtitle>
        </S.Card>
      </S.PageContainer>
    );
  }

  return (
    <S.PageContainer>
      {/* 1. Global Alert Configuration */}
      <S.Card>
        <S.CardHeader>
          <S.CardHeaderLeft>
            <S.CardTitle>
              Proactive Connect Error Monitoring
              <S.StatusBadge $active={formState.enabled}>
                {formState.enabled ? 'ACTIVE' : 'DISABLED'}
              </S.StatusBadge>
            </S.CardTitle>
            <S.CardSubtitle>
              Automatically detect failing connectors and tasks across all registered Kafka Connect workers.
            </S.CardSubtitle>
          </S.CardHeaderLeft>
          <Switch
            name="enabled"
            checked={formState.enabled}
            onChange={() =>
              setFormState((prev) => ({
                ...prev,
                enabled: !prev.enabled,
              }))
            }
          />
        </S.CardHeader>

        <S.FormRow>
          <S.FormGroup>
            <S.Label htmlFor="polling-interval">Polling Interval (Seconds)</S.Label>
            <InputStyled.Input
              id="polling-interval"
              inputSize="M"
              search={false}
              type="number"
              min={5}
              value={Math.round(formState.pollingIntervalMs / 1000)}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                const val = parseInt(e.target.value, 10);
                setFormState((prev) => ({
                  ...prev,
                  pollingIntervalMs: isNaN(val) ? 30000 : Math.max(5, val) * 1000,
                }));
              }}
            />
            <S.HelpText>Frequency at which Kafka Connect clusters are polled for errors (min 5s).</S.HelpText>
          </S.FormGroup>

          <S.FormGroup>
            <S.Label htmlFor="throttle-interval">Throttle Repeat Alerts (Minutes)</S.Label>
            <InputStyled.Input
              id="throttle-interval"
              inputSize="M"
              search={false}
              type="number"
              min={1}
              value={formState.throttleIntervalMinutes}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                const val = parseInt(e.target.value, 10);
                setFormState((prev) => ({
                  ...prev,
                  throttleIntervalMinutes: isNaN(val) ? 15 : Math.max(1, val),
                }));
              }}
            />
            <S.HelpText>Suppress repeat alerts for the same connector/task error within this window.</S.HelpText>
          </S.FormGroup>
        </S.FormRow>
      </S.Card>

      {/* 2. Slack Notification Channel */}
      <S.Card>
        <S.CardHeader>
          <S.CardHeaderLeft>
            <S.CardTitle>
              Slack Notifications
              <S.StatusBadge $active={formState.slack.enabled}>
                {formState.slack.enabled ? 'ENABLED' : 'DISABLED'}
              </S.StatusBadge>
            </S.CardTitle>
            <S.CardSubtitle>
              Post rich Block Kit alert messages to a designated Slack channel via Incoming Webhooks.
            </S.CardSubtitle>
          </S.CardHeaderLeft>
          <Switch
            name="slack-enabled"
            checked={formState.slack.enabled}
            onChange={() =>
              setFormState((prev) => ({
                ...prev,
                slack: {
                  ...prev.slack,
                  enabled: !prev.slack.enabled,
                },
              }))
            }
          />
        </S.CardHeader>

        <S.FormGroup>
          <S.Label htmlFor="slack-webhook">Webhook URL</S.Label>
          <S.PasswordWrapper>
            <InputStyled.Input
              id="slack-webhook"
              inputSize="M"
              search={false}
              type={showSlackWebhook ? 'text' : 'password'}
              placeholder="https://hooks.slack.com/services/T00/B00/XXXX"
              value={formState.slack.webhookUrl || ''}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                setFormState((prev) => ({
                  ...prev,
                  slack: {
                    ...prev.slack,
                    webhookUrl: e.target.value,
                  },
                }))
              }
            />
            <S.PasswordToggleBtn
              type="button"
              onClick={() => setShowSlackWebhook((prev) => !prev)}
            >
              {showSlackWebhook ? 'Hide' : 'Show'}
            </S.PasswordToggleBtn>
          </S.PasswordWrapper>
          <S.HelpText>Standard Slack incoming webhook URL generated from your Slack App configuration.</S.HelpText>
        </S.FormGroup>

        <S.FormGroup>
          <S.Label htmlFor="slack-channel">Channel Override (Optional)</S.Label>
          <InputStyled.Input
            id="slack-channel"
            inputSize="M"
            search={false}
            type="text"
            placeholder="#kafka-alerts"
            value={formState.slack.channel || ''}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setFormState((prev) => ({
                ...prev,
                slack: {
                  ...prev.slack,
                  channel: e.target.value,
                },
              }))
            }
          />
          <S.HelpText>Optional Slack channel name (e.g. #connect-alerts) if webhook permits channel override.</S.HelpText>
        </S.FormGroup>

        <S.TestActionsRow>
          <Button
            buttonType="secondary"
            buttonSize="S"
            disabled={!formState.slack.webhookUrl || testingChannel === 'slack'}
            inProgress={testingChannel === 'slack'}
            onClick={() => handleTestAlert('slack')}
          >
            Send Test Slack Notification
          </Button>
        </S.TestActionsRow>
      </S.Card>

      {/* 3. Email Notification Channel */}
      <S.Card>
        <S.CardHeader>
          <S.CardHeaderLeft>
            <S.CardTitle>
              Email (SMTP) Notifications
              <S.StatusBadge $active={formState.email.enabled}>
                {formState.email.enabled ? 'ENABLED' : 'DISABLED'}
              </S.StatusBadge>
            </S.CardTitle>
            <S.CardSubtitle>
              Deliver formatted HTML error alerts directly to operations and engineer mailboxes.
            </S.CardSubtitle>
          </S.CardHeaderLeft>
          <Switch
            name="email-enabled"
            checked={formState.email.enabled}
            onChange={() =>
              setFormState((prev) => ({
                ...prev,
                email: {
                  ...prev.email,
                  enabled: !prev.email.enabled,
                },
              }))
            }
          />
        </S.CardHeader>

        <S.FormRow>
          <S.FormGroup>
            <S.Label htmlFor="smtp-host">SMTP Host</S.Label>
            <InputStyled.Input
              id="smtp-host"
              inputSize="M"
              search={false}
              type="text"
              placeholder="smtp.gmail.com"
              value={formState.email.smtpHost || ''}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                setFormState((prev) => ({
                  ...prev,
                  email: {
                    ...prev.email,
                    smtpHost: e.target.value,
                  },
                }))
              }
            />
          </S.FormGroup>

          <S.FormGroup>
            <S.Label htmlFor="smtp-port">SMTP Port</S.Label>
            <InputStyled.Input
              id="smtp-port"
              inputSize="M"
              search={false}
              type="number"
              placeholder="587"
              value={formState.email.smtpPort || 587}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                const val = parseInt(e.target.value, 10);
                setFormState((prev) => ({
                  ...prev,
                  email: {
                    ...prev.email,
                    smtpPort: isNaN(val) ? 587 : val,
                  },
                }));
              }}
            />
          </S.FormGroup>
        </S.FormRow>

        <S.FormRow>
          <S.FormGroup>
            <S.Label htmlFor="smtp-user">Username</S.Label>
            <InputStyled.Input
              id="smtp-user"
              inputSize="M"
              search={false}
              type="text"
              placeholder="alerts@company.com"
              value={formState.email.username || ''}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                setFormState((prev) => ({
                  ...prev,
                  email: {
                    ...prev.email,
                    username: e.target.value,
                  },
                }))
              }
            />
          </S.FormGroup>

          <S.FormGroup>
            <S.Label htmlFor="smtp-pass">Password</S.Label>
            <S.PasswordWrapper>
              <InputStyled.Input
                id="smtp-pass"
                inputSize="M"
                search={false}
                type={showEmailPassword ? 'text' : 'password'}
                placeholder="********"
                value={formState.email.password || ''}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  setFormState((prev) => ({
                    ...prev,
                    email: {
                      ...prev.email,
                      password: e.target.value,
                    },
                  }))
                }
              />
              <S.PasswordToggleBtn
                type="button"
                onClick={() => setShowEmailPassword((prev) => !prev)}
              >
                {showEmailPassword ? 'Hide' : 'Show'}
              </S.PasswordToggleBtn>
            </S.PasswordWrapper>
          </S.FormGroup>
        </S.FormRow>

        <S.FormRow>
          <S.CheckboxLabel>
            <input
              type="checkbox"
              checked={formState.email.starttlsEnabled ?? true}
              onChange={(e) =>
                setFormState((prev) => ({
                  ...prev,
                  email: {
                    ...prev.email,
                    starttlsEnabled: e.target.checked,
                  },
                }))
              }
            />
            Enable STARTTLS (Recommended for Port 587)
          </S.CheckboxLabel>

          <S.CheckboxLabel>
            <input
              type="checkbox"
              checked={formState.email.sslEnabled ?? false}
              onChange={(e) =>
                setFormState((prev) => ({
                  ...prev,
                  email: {
                    ...prev.email,
                    sslEnabled: e.target.checked,
                  },
                }))
              }
            />
            Enable SSL / SMTPS (Port 465)
          </S.CheckboxLabel>
        </S.FormRow>

        <S.FormGroup style={{ marginTop: '16px' }}>
          <S.Label htmlFor="email-from">Sender Address ("From")</S.Label>
          <InputStyled.Input
            id="email-from"
            inputSize="M"
            search={false}
            type="email"
            placeholder="kafbat-alerts@company.com"
            value={formState.email.from || ''}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setFormState((prev) => ({
                ...prev,
                email: {
                  ...prev.email,
                  from: e.target.value,
                },
              }))
            }
          />
        </S.FormGroup>

        <S.FormGroup>
          <S.Label htmlFor="email-recipients">Recipients (Comma-Separated)</S.Label>
          <InputStyled.Input
            id="email-recipients"
            inputSize="M"
            search={false}
            type="text"
            placeholder="oncall@company.com, devops@company.com"
            value={recipientsInput}
            onChange={handleRecipientsChange}
          />
          <S.HelpText>List of recipient email addresses separated by commas.</S.HelpText>
        </S.FormGroup>

        <S.TestActionsRow>
          <Button
            buttonType="secondary"
            buttonSize="S"
            disabled={!formState.email.smtpHost || testingChannel === 'email'}
            inProgress={testingChannel === 'email'}
            onClick={() => handleTestAlert('email')}
          >
            Send Test Email Notification
          </Button>
        </S.TestActionsRow>
      </S.Card>

      {/* Save Action Bar */}
      <S.SaveBar>
        <Button
          buttonType="secondary"
          buttonSize="M"
          onClick={handleReset}
          disabled={updateMutation?.isPending}
        >
          Reset to Saved
        </Button>
        <Button
          buttonType="primary"
          buttonSize="M"
          onClick={handleSave}
          inProgress={updateMutation?.isPending}
        >
          Save Settings
        </Button>
      </S.SaveBar>
    </S.PageContainer>
  );
};

export default ConnectAlertsPage;
