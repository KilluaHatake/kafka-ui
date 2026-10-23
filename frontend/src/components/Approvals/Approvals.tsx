import React, { useState } from 'react';
import useAppParams from 'lib/hooks/useAppParams';
import { ClusterNameRoute } from 'lib/paths';
import { Button } from 'components/common/Button/Button';
import { Tag } from 'components/common/Tag/Tag.styled';
import PageLoader from 'components/common/PageLoader/PageLoader';
import {
  useApprovals,
  useApproveRequest,
  useRejectRequest,
  ApprovalRequest,
  ApprovalStatus,
  ApprovalTargetType,
} from 'lib/hooks/api/approvals';
import * as S from './Approvals.styled';
import ApprovalDetailsModal from './ApprovalDetailsModal';
import RejectModal from './RejectModal';
import ApprovalSettingsModal from './ApprovalSettingsModal';

const getTagColor = (status: string): 'yellow' | 'green' | 'red' | 'gray' => {
  switch (status) {
    case 'PENDING':
      return 'yellow';
    case 'EXECUTED':
      return 'green';
    case 'REJECTED':
    case 'EXECUTION_FAILED':
      return 'red';
    default:
      return 'gray';
  }
};

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
    default:
      return type;
  }
};

const Approvals: React.FC = () => {
  const { clusterName } = useAppParams<ClusterNameRoute>();

  const [statusFilter, setStatusFilter] = useState<string>('');
  const [typeFilter, setTypeFilter] = useState<string>('');

  const [selectedRequest, setSelectedRequest] = useState<ApprovalRequest | null>(null);
  const [isDetailsOpen, setIsDetailsOpen] = useState(false);
  const [rejectRequestTarget, setRejectRequestTarget] = useState<ApprovalRequest | null>(null);
  const [isRejectOpen, setIsRejectOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);

  const { data: requests = [], isLoading, refetch } = useApprovals(
    clusterName,
    (statusFilter as ApprovalStatus) || undefined,
    (typeFilter as ApprovalTargetType) || undefined
  );

  const approveMutation = useApproveRequest(clusterName);
  const rejectMutation = useRejectRequest(clusterName);

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
          <div style={{ fontSize: '13px', color: '#777', marginTop: '4px' }}>
            Cluster: <strong>{clusterName}</strong> • {pendingCount} Pending Change{pendingCount === 1 ? '' : 's'}
          </div>
        </div>
        <S.HeaderActions>
          <Button
            buttonType="secondary"
            buttonSize="M"
            onClick={() => setIsSettingsOpen(true)}
          >
            Workflow Settings
          </Button>
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
                      <Tag color={getTagColor(req.status)}>{req.status}</Tag>
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
                              inProgress={approveMutation.isPending}
                            >
                              Approve
                            </Button>
                            <Button
                              buttonType="danger"
                              buttonSize="S"
                              onClick={() => handleRejectClick(req)}
                              inProgress={rejectMutation.isPending}
                            >
                              Reject
                            </Button>
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
