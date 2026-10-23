import React, { type FC, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClusterBrokerParam } from 'lib/paths';
import useAppParams from 'lib/hooks/useAppParams';
import { handleApprovalRedirect } from 'lib/hooks/api/approvals';
import {
  useBrokerConfig,
  useUpdateBrokerConfigByName,
} from 'lib/hooks/api/brokers';
import Table from 'components/common/NewTable';
import type { BrokerConfig } from 'generated-sources';
import Search from 'components/common/Search/Search';
import {
  getBrokerConfigsTableColumns,
  getConfigTableData,
} from 'components/Brokers/Broker/Configs/lib/utils';

import * as S from './Configs.styled';

const Configs: FC = () => {
  const [searchQuery, setSearchQuery] = useState('');
  const navigate = useNavigate();
  const { clusterName, brokerId } = useAppParams<ClusterBrokerParam>();
  const { data: configs = [] } = useBrokerConfig(clusterName, Number(brokerId));
  const updateBrokerConfigByName = useUpdateBrokerConfigByName(
    clusterName,
    Number(brokerId)
  );

  const tableData = useMemo(
    () => getConfigTableData(configs, searchQuery),
    [configs, searchQuery]
  );

  const onUpdateInputCell = async (
    name: BrokerConfig['name'],
    value: BrokerConfig['value']
  ) => {
    try {
      const res: any = await updateBrokerConfigByName.mutateAsync({
        name,
        brokerConfigItem: { value },
      });
      handleApprovalRedirect(
        res,
        clusterName,
        navigate,
        'Broker configuration update submitted to approval queue.'
      );
    } catch (e: any) {
      handleApprovalRedirect(
        e,
        clusterName,
        navigate,
        'Broker configuration update submitted to approval queue.'
      );
    }
  };

  const columns = useMemo(
    () => getBrokerConfigsTableColumns(onUpdateInputCell),
    []
  );

  return (
    <>
      <S.SearchWrapper>
        <Search
          onChange={setSearchQuery}
          placeholder="Search by Key or Value"
          value={searchQuery}
        />
      </S.SearchWrapper>
      <Table columns={columns} data={tableData} />
    </>
  );
};

export default Configs;
