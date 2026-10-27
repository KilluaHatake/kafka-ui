import React from 'react';
import { screen, fireEvent } from '@testing-library/react';
import { render, WithRoute } from 'lib/testHelpers';
import { clusterApprovalsPath } from 'lib/paths';
import Approvals from 'components/Approvals/Approvals';
import {
  useApprovals,
  useApproveRequest,
  useRejectRequest,
  useDeleteApproval,
  useApprovalPermissions,
  useApprovalSettings,
  ApprovalRequest,
} from 'lib/hooks/api/approvals';

jest.mock('lib/hooks/api/approvals', () => ({
  useApprovals: jest.fn(),
  useApproveRequest: jest.fn(),
  useRejectRequest: jest.fn(),
  useDeleteApproval: jest.fn(() => ({ mutate: jest.fn(), mutateAsync: jest.fn(), isPending: false })),
  useApprovalPermissions: jest.fn(() => ({ data: { username: 'test-user', roles: ['Architect'], canApprove: true } })),
  useApprovalSettings: jest.fn(),
  useUpdateApprovalSettings: jest.fn(() => ({ mutate: jest.fn(), isPending: false })),
}));

jest.mock('components/common/DiffViewer/DiffViewer', () => ({ value }: { value: [string, string] }) => (
  <div data-testid="diff-viewer">
    <div data-testid="diff-old">{value[0]}</div>
    <div data-testid="diff-new">{value[1]}</div>
  </div>
));

const mockRequests: ApprovalRequest[] = [
  {
    id: 'req-1',
    clusterName: 'local',
    type: 'TOPIC_CREATE',
    targetName: 'new-orders',
    requesterEmail: 'dev@company.com',
    requesterRoles: ['Developer'],
    status: 'PENDING',
    createdAt: '2026-10-22T10:00:00Z',
    updatedAt: '2026-10-22T10:00:00Z',
    newConfig: { 'cleanup.policy': 'compact', 'ssl.keystore.password': 'cleartext-secret' },
    metadata: { 'api.token': 'secret-token' },
  },
  {
    id: 'req-2',
    clusterName: 'local',
    type: 'CONNECTOR_CREATE',
    targetName: 's3-sink',
    connectName: 'connect-01',
    requesterEmail: 'dev2@company.com',
    requesterRoles: ['Developer'],
    status: 'EXECUTED',
    approverEmail: 'arch@company.com',
    createdAt: '2026-10-22T09:00:00Z',
    updatedAt: '2026-10-22T09:15:00Z',
    newConfig: { 'connector.class': 'S3SinkConnector' },
  },
];

describe('Approvals Page', () => {
  const mockApproveMutate = jest.fn();
  const mockRejectMutate = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();

    (useApprovals as jest.Mock).mockReturnValue({
      data: mockRequests,
      isLoading: false,
      refetch: jest.fn(),
    });

    (useApproveRequest as jest.Mock).mockReturnValue({
      mutate: mockApproveMutate,
      isPending: false,
    });

    (useRejectRequest as jest.Mock).mockReturnValue({
      mutate: mockRejectMutate,
      isPending: false,
    });

    (useDeleteApproval as jest.Mock).mockReturnValue({
      mutate: jest.fn(),
      mutateAsync: jest.fn(),
      isPending: false,
    });

    (useApprovalPermissions as jest.Mock).mockReturnValue({
      data: { username: 'test-user', roles: ['Architect'], canApprove: true },
      isLoading: false,
    });

    (useApprovalSettings as jest.Mock).mockReturnValue({
      data: {
        enabled: true,
        topic: '__kui-approval-requests',
        requireTopicCreation: true,
        requireTopicConfigUpdates: true,
        requireConnectorDeployments: true,
        requireBrokerConfigUpdates: true,
        requireAlertSettingsUpdates: true,
        requireConsumerGroupDelete: true,
        requireConnectorResetOffsets: true,
        approverRoles: ['Architect'],
        directApplyRoles: ['Architect'],
      },
      isLoading: false,
    });
  });

  const renderComponent = () =>
    render(
      <WithRoute path={clusterApprovalsPath()}>
        <Approvals />
      </WithRoute>,
      { initialEntries: [clusterApprovalsPath('local')] }
    );

  it('renders table with requests and header info', () => {
    renderComponent();

    expect(screen.getByText('Approval Requests')).toBeInTheDocument();
    expect(screen.getByText(/1 Pending Change/)).toBeInTheDocument();
    expect(screen.getByText('new-orders')).toBeInTheDocument();
    expect(screen.getByText('s3-sink')).toBeInTheDocument();
    expect(screen.getByText('Create Topic')).toBeInTheDocument();
    expect(screen.getByText('Create Connector')).toBeInTheDocument();
    expect(screen.getByText('dev@company.com')).toBeInTheDocument();
  });

  it('handles approving a pending request directly from table', () => {
    renderComponent();

    const approveBtn = screen.getByRole('button', { name: 'Approve' });
    fireEvent.click(approveBtn);

    expect(mockApproveMutate).toHaveBeenCalledWith('req-1', expect.any(Object));
  });

  it('opens details modal when clicking Diff / Details and masks sensitive credentials', () => {
    renderComponent();

    const diffButtons = screen.getAllByRole('button', { name: 'Diff / Details' });
    fireEvent.click(diffButtons[0]);

    expect(screen.getByText('Current Configuration')).toBeInTheDocument();
    expect(screen.getByText('Proposed Configuration')).toBeInTheDocument();
    expect(screen.getByTestId('diff-viewer')).toBeInTheDocument();
    expect(screen.getByTestId('diff-new')).toHaveTextContent('"ssl.keystore.password": "******"');
    expect(screen.queryByText('cleartext-secret')).not.toBeInTheDocument();
    expect(screen.getByText(/"api\.token":\s*"\*\*\*\*\*\*"/)).toBeInTheDocument();
    expect(screen.queryByText('secret-token')).not.toBeInTheDocument();
  });

  it('opens reject modal and confirms rejection', () => {
    renderComponent();

    const rejectBtn = screen.getByRole('button', { name: 'Reject' });
    fireEvent.click(rejectBtn);

    expect(screen.getByText(/Please provide a reason for rejecting/)).toBeInTheDocument();

    const reasonInput = screen.getByPlaceholderText(/e\.g\. Memory limits too low/);
    fireEvent.change(reasonInput, { target: { value: 'Invalid topic cleanup policy' } });

    const confirmBtn = screen.getByRole('button', { name: 'Confirm Rejection' });
    fireEvent.click(confirmBtn);

    expect(mockRejectMutate).toHaveBeenCalledWith(
      { id: 'req-1', reason: 'Invalid topic cleanup policy' },
      expect.any(Object)
    );
  });

  it('disables Approve and shows Delete button for developer role', () => {
    (useApprovalPermissions as jest.Mock).mockReturnValue({
      data: { username: 'dev-user', roles: ['developer'], canApprove: false },
      isLoading: false,
    });

    renderComponent();

    // Approve button should be disabled for developer
    const approveBtn = screen.getByText('Approve').closest('button');
    expect(approveBtn).toBeDisabled();

    // Instead of Reject, Delete button should be displayed
    expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument();
    const deleteBtn = screen.getByRole('button', { name: 'Delete' });
    expect(deleteBtn).toBeInTheDocument();
  });

  it('hides Workflow Settings button when canManageWorkflowSettings is false', () => {
    (useApprovalPermissions as jest.Mock).mockReturnValue({
      data: {
        username: 'dev-user',
        roles: ['developer'],
        canApprove: false,
        canManageWorkflowSettings: false,
      },
      isLoading: false,
    });

    renderComponent();

    expect(screen.queryByRole('button', { name: 'Workflow Settings' })).not.toBeInTheDocument();
  });
});
