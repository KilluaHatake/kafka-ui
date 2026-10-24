import React, { useState } from 'react';
import useAppParams from 'lib/hooks/useAppParams';
import { ClusterNameRoute } from 'lib/paths';
import { Button } from 'components/common/Button/Button';
import PageLoader from 'components/common/PageLoader/PageLoader';
import { useConfirm } from 'lib/hooks/useConfirm';
import {
  useApprovals,
  useApproveRequest,
  useRejectRequest,
  useDeleteApproval,
  useApprovalPermissions,
  ApprovalRequest,
  ApprovalStatus,
  ApprovalTargetType,
} from 'lib/hooks/api/approvals';
import * as S from './Approvals.styled';
import ApprovalDetailsModal from './ApprovalDetailsModal';
import RejectModal from './RejectModal';
import ApprovalSettingsModal from './ApprovalSettingsModal';

const formatType = (type: ApprovalTargetType): string => {
  switch (type) {
    case 'TOPIC_CREATE':
      return 'Create Topic';
    case 'TOPIC_CONFIG_UPDATE':
      return 'Update Topic Config';
    case 'CONNECTOR_CREATE':
      return 'Create Connector';
    case 'CONNECTOR_UPDATE':
      return 'Update Connector Config';
    case 'CONNECTOR_ACTION':
      return 'Connector Action';
    case 'BROKER_CONFIG_UPDATE':
      return 'Update Broker Config';
    case 'ALERT_SETTINGS_UPDATE':
      return 'Update Alert Settings';
    case 'CONSUMER_GROUP_DELETE':
      return 'Delete Consumer Group';
    case 'CONNECTOR_RESET_OFFSETS':
      return 'Reset Connector Offsets';
    default:
      return type;
  }
};

const Approvals: React.FC = () => {
  const { clusterName } = useAppParams<ClusterNameRoute>();
  const confirm = useConfirm(true);

  const [statusFilter, setStatusFilter] = useState<string>('');
  const [typeFilter, setTypeFilter] = useState<string>('');

  const [selectedRequest, setSelectedRequest] = useState<ApprovalRequest | null>(null);
  const [isDetailsOpen, setIsDetailsOpen] = useState(false);
  const [rejectRequestTarget, setRejectRequestTarget] = useState<ApprovalRequest | null>(null);
  const [isRejectOpen, setIsRejectOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);

  const permissionsQuery = useApprovalPermissions(clusterName);
  const permissions = permissionsQuery?.data;
  const canApprove = permissions?.canApprove ?? true;
  const canManageWorkflowSettings = permissions ? permissions.canManageWorkflowSettings !== false : true;

  const { data: requests = [], isLoading, refetch } = useApprovals(
    clusterName,
    (statusFilter as ApprovalStatus) || undefined,
    (typeFilter as ApprovalTargetType) || undefined
  );

  const approveMutation = useApproveRequest(clusterName);
  const rejectMutation = useRejectRequest(clusterName);
  const deleteMutation = useDeleteApproval(clusterName);

  const handleApprove = (id: string) => {
    approveMutation.mutate(id, {
      onSuccess: () => {
        setIsDetailsOpen(false);
        refetch();
      },
    });
  };

  const handleRejectClick = (req: ApprovalRequest) => {
    setRejectRequestTarget(req);
    setIsRejectOpen(true);
  };

  const handleDeleteClick = (req: ApprovalRequest) => {
    confirm(
      <>
        Are you sure you want to delete the pending approval request for{' '}
        <b>{req.targetName}</b>?
        <br />
        <span style={{ fontSize: '12px', opacity: 0.8 }}>
          This will remove the request from the queue so you can raise a fresh request with changes.
        </span>
      </>,
      async () => {
        await deleteMutation.mutateAsync(req.id);
        setIsDetailsOpen(false);
        refetch();
      }
    );
  };

  const handleConfirmReject = (id: string, reason: string) => {
    rejectMutation.mutate(
      { id, reason },
      {
        onSuccess: () => {
          setIsRejectOpen(false);
          setIsDetailsOpen(false);
          refetch();
        },
      }
    );
  };

  const pendingCount = requests.filter((r) => r.status === 'PENDING').length;

  return (
    <S.Wrapper>
      <S.Header>
        <div>
          <S.Title>Approval Requests</S.Title>
          <S.Subtitle>
            Cluster: <strong>{clusterName}</strong> • {pendingCount} Pending Change{pendingCount === 1 ? '' : 's'}
          </S.Subtitle>
        </div>
        <S.HeaderActions>
          {canManageWorkflowSettings && (
            <Button
              buttonType="secondary"
              buttonSize="M"
              onClick={() => setIsSettingsOpen(true)}
            >
              Workflow Settings
            </Button>
          )}
          <Button
            buttonType="primary"
            buttonSize="M"
            onClick={() => refetch()}
          >
            Refresh
          </Button>
        </S.HeaderActions>
      </S.Header>

      <S.Filters>
        <S.FilterItem>
          <label htmlFor="status-filter">Status:</label>
          <select
            id="status-filter"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="">All Statuses</option>
            <option value="PENDING">PENDING</option>
            <option value="EXECUTED">EXECUTED</option>
            <option value="REJECTED">REJECTED</option>
            <option value="EXECUTION_FAILED">EXECUTION_FAILED</option>
          </select>
        </S.FilterItem>

        <S.FilterItem>
          <label htmlFor="type-filter">Change Type:</label>
          <select
            id="type-filter"
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value)}
          >
            <option value="">All Types</option>
            <option value="TOPIC_CREATE">Topic Creation</option>
            <option value="TOPIC_CONFIG_UPDATE">Topic Config Update</option>
            <option value="CONNECTOR_CREATE">Connector Creation</option>
            <option value="CONNECTOR_UPDATE">Connector Config Update</option>
            <option value="CONNECTOR_ACTION">Connector State Action</option>
            <option value="BROKER_CONFIG_UPDATE">Broker Config Update</option>
            <option value="ALERT_SETTINGS_UPDATE">Alert Settings Update</option>
            <option value="CONSUMER_GROUP_DELETE">Delete Consumer Group</option>
            <option value="CONNECTOR_RESET_OFFSETS">Reset Connector Offsets</option>
          </select>
        </S.FilterItem>
      </S.Filters>

      {isLoading ? (
        <PageLoader />
      ) : (
        <S.TableContainer>
          <S.Table>
            <thead>
              <tr>
                <th>Target</th>
                <th>Type</th>
                <th>Connect / Scope</th>
                <th>Requested By</th>
                <th>Submitted At</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {requests.length === 0 ? (
                <tr>
                  <td colSpan={7}>
                    <S.EmptyState>No approval requests found matching the filter.</S.EmptyState>
                  </td>
                </tr>
              ) : (
                requests.map((req) => (
                  <tr key={req.id}>
                    <td>
                      <strong style={{ cursor: 'pointer' }} onClick={() => { setSelectedRequest(req); setIsDetailsOpen(true); }}>
                        {req.targetName}
                      </strong>
                    </td>
                    <td>{formatType(req.type)}</td>
                    <td>{req.connectName || 'Cluster'}</td>
                    <td>{req.requesterEmail}</td>
                    <td>{new Date(req.createdAt).toLocaleString()}</td>
                    <td>
                      <S.StatusBadge $status={req.status}>{req.status}</S.StatusBadge>
                    </td>
                    <td>
                      <S.ActionGroup>
                        <Button
                          buttonType="secondary"
                          buttonSize="S"
                          onClick={() => {
                            setSelectedRequest(req);
                            setIsDetailsOpen(true);
                          }}
                        >
                          Diff / Details
                        </Button>
                        {req.status === 'PENDING' && (
                          <>
                            <Button
                              buttonType="primary"
                              buttonSize="S"
                              onClick={() => handleApprove(req.id)}
                              disabled={!canApprove}
                              title={
                                !canApprove
                                  ? 'Only Approver roles (Architect/Admin) can approve requests'
                                  : undefined
                              }
                              inProgress={approveMutation?.isPending}
                            >
                              Approve
                            </Button>
                            {canApprove ? (
                              <Button
                                buttonType="danger"
                                buttonSize="S"
                                onClick={() => handleRejectClick(req)}
                                inProgress={rejectMutation?.isPending}
                              >
                                Reject
                              </Button>
                            ) : (
                              <Button
                                buttonType="danger"
                                buttonSize="S"
                                onClick={() => handleDeleteClick(req)}
                                inProgress={deleteMutation?.isPending}
                              >
                                Delete
                              </Button>
                            )}
                          </>
                        )}
                      </S.ActionGroup>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </S.Table>
        </S.TableContainer>
      )}

      <ApprovalDetailsModal
        isOpen={isDetailsOpen}
        request={selectedRequest}
        onClose={() => setIsDetailsOpen(false)}
        onApprove={handleApprove}
        onRejectClick={handleRejectClick}
        onDeleteClick={handleDeleteClick}
        isApprover={canApprove}
      />

      <RejectModal
        isOpen={isRejectOpen}
        request={rejectRequestTarget}
        onClose={() => setIsRejectOpen(false)}
        onConfirmReject={handleConfirmReject}
      />

      <ApprovalSettingsModal
        isOpen={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
      />
    </S.Wrapper>
  );
};

export default Approvals;
