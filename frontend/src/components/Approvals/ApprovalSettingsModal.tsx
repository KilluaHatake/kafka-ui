import React, { useState, useEffect } from 'react';
import Modal from 'components/common/Modal/Modal';
import { Button } from 'components/common/Button/Button';
import * as S from './Approvals.styled';
import {
  useApprovalSettings,
  useUpdateApprovalSettings,
  ApprovalProperties,
} from 'lib/hooks/api/approvals';

interface Props {
  isOpen: boolean;
  onClose: () => void;
}

const ApprovalSettingsModal: React.FC<Props> = ({ isOpen, onClose }) => {
  const { data: settings, isLoading } = useApprovalSettings(isOpen);
  const updateSettings = useUpdateApprovalSettings();

  const [formState, setFormState] = useState<ApprovalProperties>({
    enabled: true,
    topic: '__kui-approval-requests',
    requireTopicCreation: true,
    requireTopicConfigUpdates: true,
    requireConnectorDeployments: true,
    requireBrokerConfigUpdates: true,
    requireAlertSettingsUpdates: true,
    requireConsumerGroupDelete: true,
    requireConnectorResetOffsets: true,
    approverRoles: ['Architect', 'Admin'],
    directApplyRoles: ['Architect', 'Admin'],
  });

  useEffect(() => {
    if (settings) {
      setFormState(settings);
    }
  }, [settings]);

  const handleSave = () => {
    updateSettings.mutate(formState, {
      onSuccess: () => {
        onClose();
      },
    });
  };

  const footer = (
    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
      <Button buttonType="secondary" buttonSize="M" onClick={onClose}>
        Cancel
      </Button>
      <Button
        buttonType="primary"
        buttonSize="M"
        onClick={handleSave}
        inProgress={updateSettings?.isPending}
      >
        Save Settings
      </Button>
    </div>
  );

  if (!isOpen) {
    return null;
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Approval Workflow Settings"
      footer={footer}
      maxWidth="550px"
    >
      {isLoading ? (
        <div>Loading settings...</div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div>
            <S.SettingCheckboxLabel>
              <input
                type="checkbox"
                checked={formState.requireTopicCreation}
                onChange={(e) =>
                  setFormState((prev) => ({
                    ...prev,
                    requireTopicCreation: e.target.checked,
                  }))
                }
              />
              Require approval for Topic Creation
            </S.SettingCheckboxLabel>
            <S.SettingDescription>
              When enabled, non-architects cannot create topics directly.
            </S.SettingDescription>
          </div>

          <div>
            <S.SettingCheckboxLabel>
              <input
                type="checkbox"
                checked={formState.requireTopicConfigUpdates}
                onChange={(e) =>
                  setFormState((prev) => ({
                    ...prev,
                    requireTopicConfigUpdates: e.target.checked,
                  }))
                }
              />
              Require approval for Topic Configuration Changes
            </S.SettingCheckboxLabel>
            <S.SettingDescription>
              When enabled, topic property edits require approval.
            </S.SettingDescription>
          </div>

          <div>
            <S.SettingCheckboxLabel>
              <input
                type="checkbox"
                checked={formState.requireConnectorDeployments}
                onChange={(e) =>
                  setFormState((prev) => ({
                    ...prev,
                    requireConnectorDeployments: e.target.checked,
                  }))
                }
              />
              Require approval for Kafka Connect Deployments & Mutations
            </S.SettingCheckboxLabel>
            <S.SettingDescription>
              When enabled, creating or editing connectors requires architect approval.
            </S.SettingDescription>
          </div>

          <div>
            <S.SettingCheckboxLabel>
              <input
                type="checkbox"
                checked={formState.requireBrokerConfigUpdates}
                onChange={(e) =>
                  setFormState((prev) => ({
                    ...prev,
                    requireBrokerConfigUpdates: e.target.checked,
                  }))
                }
              />
              Require approval for Broker Configuration Changes
            </S.SettingCheckboxLabel>
            <S.SettingDescription>
              When enabled, updating broker configuration properties requires architect review.
            </S.SettingDescription>
          </div>

          <div>
            <S.SettingCheckboxLabel>
              <input
                type="checkbox"
                checked={formState.requireAlertSettingsUpdates}
                onChange={(e) =>
                  setFormState((prev) => ({
                    ...prev,
                    requireAlertSettingsUpdates: e.target.checked,
                  }))
                }
              />
              Require approval for Error Alert Notification Settings
            </S.SettingCheckboxLabel>
            <S.SettingDescription>
              When enabled, changing Slack or Email alert notification settings requires approval.
            </S.SettingDescription>
          </div>

          <div>
            <S.SettingCheckboxLabel>
              <input
                type="checkbox"
                checked={formState.requireConsumerGroupDelete}
                onChange={(e) =>
                  setFormState((prev) => ({
                    ...prev,
                    requireConsumerGroupDelete: e.target.checked,
                  }))
                }
              />
              Require approval for Consumer Group Deletion
            </S.SettingCheckboxLabel>
            <S.SettingDescription>
              When enabled, deleting a consumer group requires architect approval.
            </S.SettingDescription>
          </div>

          <div>
            <S.SettingCheckboxLabel>
              <input
                type="checkbox"
                checked={formState.requireConnectorResetOffsets}
                onChange={(e) =>
                  setFormState((prev) => ({
                    ...prev,
                    requireConnectorResetOffsets: e.target.checked,
                  }))
                }
              />
              Require approval for Kafka Connect Offset Resets
            </S.SettingCheckboxLabel>
            <S.SettingDescription>
              When enabled, resetting connector offsets requires architect approval.
            </S.SettingDescription>
          </div>

          <div>
            <S.SettingInputLabel>
              Approver Roles (comma-separated):
            </S.SettingInputLabel>
            <S.SettingInput
              type="text"
              value={formState.approverRoles?.join(', ') || ''}
              onChange={(e) =>
                setFormState((prev) => ({
                  ...prev,
                  approverRoles: e.target.value
                    .split(',')
                    .map((s) => s.trim())
                    .filter(Boolean),
                }))
              }
            />
          </div>

          <div>
            <S.SettingInputLabel>
              Direct Apply Roles (Bypass Approval, comma-separated):
            </S.SettingInputLabel>
            <S.SettingInput
              type="text"
              value={formState.directApplyRoles?.join(', ') || ''}
              onChange={(e) =>
                setFormState((prev) => ({
                  ...prev,
                  directApplyRoles: e.target.value
                    .split(',')
                    .map((s) => s.trim())
                    .filter(Boolean),
                }))
              }
            />
          </div>
        </div>
      )}
    </Modal>
  );
};

export default ApprovalSettingsModal;
