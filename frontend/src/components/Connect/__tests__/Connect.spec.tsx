import React from 'react';
import { render, WithRoute } from 'lib/testHelpers';
import { screen } from '@testing-library/react';
import Connect from 'components/Connect/Connect';
import { getNonExactPath, kafkaConnectPath } from 'lib/paths';
import { useConnects } from 'lib/hooks/api/kafkaConnect';
import { useApprovalPermissions } from 'lib/hooks/api/approvals';

const ConnectCompText = {
  new: 'New Page',
  list: 'List Page',
  details: 'Details Page',
};

jest.mock('components/Connect/New/New', () => () => (
  <div>{ConnectCompText.new}</div>
));
jest.mock('components/Connect/List/ListPage', () => () => (
  <div>{ConnectCompText.list}</div>
));
jest.mock('components/Connect/Details/DetailsPage', () => () => (
  <div>{ConnectCompText.details}</div>
));
jest.mock('lib/hooks/api/kafkaConnect', () => ({
  useConnects: jest.fn(),
}));
jest.mock('lib/hooks/api/approvals', () => ({
  useApprovalPermissions: jest.fn(() => ({
    data: { canAccessAlertSettings: true },
  })),
}));

describe('Connect', () => {
  const renderComponent = (pathname: string, routePath: string) => {
    const useConnectsMock = useConnects as jest.Mock;
    useConnectsMock.mockReturnValue({
      data: [],
    });
    return render(
      <WithRoute path={getNonExactPath(routePath)}>
        <Connect />
      </WithRoute>,
      { initialEntries: [pathname] }
    );
  };

  it('renders header', () => {
    renderComponent(kafkaConnectPath('my-cluster'), kafkaConnectPath());

    const header = screen.getByRole('heading', { name: 'Kafka Connect' });
    expect(header).toBeInTheDocument();
  });

  it('renders navigation', () => {
    renderComponent(kafkaConnectPath('my-cluster'), kafkaConnectPath());

    const clusterNavigation = screen.getByRole('link', { name: 'Clusters' });
    expect(clusterNavigation).toBeInTheDocument();

    const connectorsNavigation = screen.getByRole('link', {
      name: 'Connectors',
    });
    expect(connectorsNavigation).toBeInTheDocument();

    const alertsNavigation = screen.getByRole('link', {
      name: 'Alert Settings',
    });
    expect(alertsNavigation).toBeInTheDocument();
  });

  it('hides Alert Settings tab when user has no access to alert settings', () => {
    (useApprovalPermissions as jest.Mock).mockReturnValue({
      data: { canAccessAlertSettings: false },
    });

    renderComponent(kafkaConnectPath('my-cluster'), kafkaConnectPath());

    expect(
      screen.queryByRole('link', { name: 'Alert Settings' })
    ).not.toBeInTheDocument();
  });
});
