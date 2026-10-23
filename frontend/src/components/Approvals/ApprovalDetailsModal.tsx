import React from 'react';
import Modal from 'components/common/Modal/Modal';
import { Button } from 'components/common/Button/Button';
import DiffViewer from 'components/common/DiffViewer/DiffViewer';
import { SchemaType } from 'generated-sources';
import { ApprovalRequest } from 'lib/hooks/api/approvals';
import * as S from './Approvals.styled';

interface Props {
  request: ApprovalRequest | null;
  isOpen: boolean;
  onClose: () => void;
  onApprove: (id: string) => void;
  onRejectClick: (request: ApprovalRequest) => void;
  onDeleteClick?: (request: ApprovalRequest) => void;
  isApprover?: boolean;
}

const ApprovalDetailsModal: React.FC<Props> = ({
  request,
  isOpen,
  onClose,
  onApprove,
  onRejectClick,
  onDeleteClick,
  isApprover = true,
}) => {
  if (!request) return null;

  const oldJson = request.oldConfig
    ? JSON.stringify(request.oldConfig, null, 2)
    : '{}';
  const newJson = request.newConfig
    ? JSON.stringify(request.newConfig, null, 2)
    : '{}';

  const hasConfigDiff =
    (request.oldConfig && Object.keys(request.oldConfig).length > 0) ||
    (request.newConfig && Object.keys(request.newConfig).length > 0);

  const footer = (
    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
      <Button buttonType="secondary" buttonSize="M" onClick={onClose}>
        Close
      </Button>
      {request.status === 'PENDING' && (
        <>
          {isApprover ? (
            <>
              <Button
                buttonType="danger"
                buttonSize="M"
                onClick={() => {
                  onClose();
                  onRejectClick(request);
                }}
              >
                Reject
              </Button>
              <Button
                buttonType="primary"
                buttonSize="M"
                onClick={() => onApprove(request.id)}
              >
                Approve & Execute
              </Button>
            </>
          ) : (
            <>
              {onDeleteClick && (
                <Button
                  buttonType="danger"
                  buttonSize="M"
                  onClick={() => {
                    onClose();
                    onDeleteClick(request);
                  }}
                >
                  Delete Request
                </Button>
              )}
              <Button
                buttonType="primary"
                buttonSize="M"
                disabled
                title="Only approver roles (Architect/Admin) can approve requests"
              >
                Approve & Execute
              </Button>
            </>
          )}
        </>
      )}
    </div>
  );

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Approval Request: ${request.targetName} (${request.type})`}
      footer={footer}
      maxWidth="75vw"
    >
      <S.DetailsContainer>
        <S.InfoGrid>
          <S.InfoItem>
            <span>Target Name</span>
            <span>{request.targetName}</span>
          </S.InfoItem>
          <S.InfoItem>
            <span>Target Type</span>
            <span>{request.type}</span>
          </S.InfoItem>
          {request.connectName && (
            <S.InfoItem>
              <span>Kafka Connect Cluster</span>
              <span>{request.connectName}</span>
            </S.InfoItem>
          )}
          <S.InfoItem>
            <span>Status</span>
            <S.StatusBadge $status={request.status}>
              {request.status}
            </S.StatusBadge>
          </S.InfoItem>
          <S.InfoItem>
            <span>Requested By</span>
            <span>{request.requesterEmail}</span>
          </S.InfoItem>
          <S.InfoItem>
            <span>Submitted At</span>
            <span>{new Date(request.createdAt).toLocaleString()}</span>
          </S.InfoItem>
          {request.approverEmail && (
            <S.InfoItem>
              <span>Reviewed By</span>
              <span>{request.approverEmail}</span>
            </S.InfoItem>
          )}
          {request.rejectionReason && (
            <S.InfoItem style={{ gridColumn: 'span 2' }}>
              <span>Rejection Reason</span>
              <span style={{ color: '#d9534f' }}>{request.rejectionReason}</span>
            </S.InfoItem>
          )}
          {request.executionError && (
            <S.InfoItem style={{ gridColumn: 'span 2' }}>
              <span>Execution Error</span>
              <span style={{ color: '#d9534f' }}>{request.executionError}</span>
            </S.InfoItem>
          )}
          {request.metadata && Object.keys(request.metadata).length > 0 && (
            <S.InfoItem style={{ gridColumn: 'span 2' }}>
              <span>Metadata</span>
              <span>{JSON.stringify(request.metadata)}</span>
            </S.InfoItem>
          )}
        </S.InfoGrid>

        {hasConfigDiff && (
          <S.DiffContainer>
            <S.DiffHeader>
              <span>Current Configuration</span>
              <span>Proposed Configuration</span>
            </S.DiffHeader>
            <DiffViewer
              schemaType={SchemaType.JSON}
              value={[oldJson, newJson]}
              isFixedHeight
              height="350px"
            />
          </S.DiffContainer>
        )}
      </S.DetailsContainer>
    </Modal>
  );
};

export default ApprovalDetailsModal;
