import React, { useState, useEffect } from 'react';
import Modal from 'components/common/Modal/Modal';
import { Button } from 'components/common/Button/Button';
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
  const { data: settings, isLoading } = useApprovalSettings();
  const updateSettings = useUpdateApprovalSettings();

  const [formState, setFormState] = useState<ApprovalProperties>({
    enabled: true,
    topic: '__kui-approval-requests',
    requireTopicCreation: true,
    requireTopicConfigUpdates: true,
    requireConnectorDeployments: true,
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
            <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontWeight: 500 }}>
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
            </label>
            <div style={{ fontSize: '12px', color: '#888', marginLeft: '24px' }}>
              When enabled, non-architects cannot create topics directly.
            </div>
          </div>

          <div>
            <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontWeight: 500 }}>
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
            </label>
            <div style={{ fontSize: '12px', color: '#888', marginLeft: '24px' }}>
              When enabled, topic property edits require approval.
            </div>
          </div>

          <div>
            <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontWeight: 500 }}>
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
            </label>
            <div style={{ fontSize: '12px', color: '#888', marginLeft: '24px' }}>
              When enabled, creating or editing connectors requires architect approval.
            </div>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '13px', fontWeight: 500, marginBottom: '4px' }}>
              Approver Roles (comma-separated):
            </label>
            <input
              type="text"
              style={{
                width: '100%',
                padding: '8px 10px',
                borderRadius: '4px',
                border: '1px solid #ccc',
                boxSizing: 'border-box',
              }}
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
            <label style={{ display: 'block', fontSize: '13px', fontWeight: 500, marginBottom: '4px' }}>
              Direct Apply Roles (Bypass Approval, comma-separated):
            </label>
            <input
              type="text"
              style={{
                width: '100%',
                padding: '8px 10px',
                borderRadius: '4px',
                border: '1px solid #ccc',
                boxSizing: 'border-box',
              }}
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
