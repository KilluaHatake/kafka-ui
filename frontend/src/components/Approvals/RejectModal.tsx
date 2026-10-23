import React, { useState } from 'react';
import Modal from 'components/common/Modal/Modal';
import { Button } from 'components/common/Button/Button';
import { ApprovalRequest } from 'lib/hooks/api/approvals';
import * as S from './Approvals.styled';

interface Props {
  request: ApprovalRequest | null;
  isOpen: boolean;
  onClose: () => void;
  onConfirmReject: (id: string, reason: string) => void;
}

const RejectModal: React.FC<Props> = ({
  request,
  isOpen,
  onClose,
  onConfirmReject,
}) => {
  const [reason, setReason] = useState('');

  if (!request) return null;

  const handleConfirm = () => {
    onConfirmReject(request.id, reason);
    setReason('');
    onClose();
  };

  const footer = (
    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
      <Button buttonType="secondary" buttonSize="M" onClick={onClose}>
        Cancel
      </Button>
      <Button buttonType="danger" buttonSize="M" onClick={handleConfirm}>
        Confirm Rejection
      </Button>
    </div>
  );

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Reject Request: ${request.targetName}`}
      footer={footer}
      maxWidth="500px"
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
        <p style={{ margin: 0, fontSize: '14px' }}>
          Please provide a reason for rejecting this change request. The requester will be able to see this reason.
        </p>
        <S.Textarea
          placeholder="e.g. Memory limits too low, invalid topic naming convention..."
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          autoFocus
        />
      </div>
    </Modal>
  );
};

export default RejectModal;
