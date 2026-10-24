import React from 'react';
import { Navigate, Routes, Route, NavLink } from 'react-router-dom';
import {
  clusterConnectorsPath,
  ClusterNameRoute,
  kafkaConnectClustersPath,
  kafkaConnectClustersRelativePath,
  clusterConnectorsRelativePath,
  kafkaConnectAlertsPath,
  kafkaConnectAlertsRelativePath,
} from 'lib/paths';
import useAppParams from 'lib/hooks/useAppParams';
import Navbar from 'components/common/Navigation/Navbar.styled';
import Clusters from 'components/Connect/Clusters/Clusters';
import { TableProvider } from 'components/common/NewTable';
import { useApprovalPermissions } from 'lib/hooks/api/approvals';

import Connectors from './List/ListPage';
import Header from './Header/Header';
import ConnectAlertsPage from './Alerts/ConnectAlertsPage';

const Connect: React.FC = () => {
  const { clusterName } = useAppParams<ClusterNameRoute>();
  const permissionsQuery = useApprovalPermissions(clusterName);
  const canAccessAlertSettings = permissionsQuery?.data ? permissionsQuery.data.canAccessAlertSettings !== false : true;

  return (
    <TableProvider>
      <Header />
      <Navbar role="navigation">
        <NavLink
          to={kafkaConnectClustersPath(clusterName)}
          className={({ isActive }) => (isActive ? 'is-active' : '')}
          end
        >
          Clusters
        </NavLink>
        <NavLink
          to={clusterConnectorsPath(clusterName)}
          className={({ isActive }) => (isActive ? 'is-active' : '')}
          end
        >
          Connectors
        </NavLink>
        {canAccessAlertSettings && (
          <NavLink
            to={kafkaConnectAlertsPath(clusterName)}
            className={({ isActive }) => (isActive ? 'is-active' : '')}
            end
          >
            Alert Settings
          </NavLink>
        )}
      </Navbar>
      <Routes>
        <Route
          index
          element={
            <Navigate to={kafkaConnectClustersPath(clusterName)} replace />
          }
        />
        <Route path={kafkaConnectClustersRelativePath} element={<Clusters />} />
        <Route path={clusterConnectorsRelativePath} element={<Connectors />} />
        {canAccessAlertSettings && (
          <Route path={kafkaConnectAlertsRelativePath} element={<ConnectAlertsPage />} />
        )}
      </Routes>
    </TableProvider>
  );
};

export default Connect;
