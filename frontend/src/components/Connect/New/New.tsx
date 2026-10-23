import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import useAppParams from 'lib/hooks/useAppParams';
import { Controller, FormProvider, useForm } from 'react-hook-form';
import { ErrorMessage } from '@hookform/error-message';
import { yupResolver } from '@hookform/resolvers/yup';
import {
  clusterConnectConnectorPath,
  clusterConnectorsPath,
  clusterApprovalsPath,
  ClusterNameRoute,
} from 'lib/paths';
import { showAlert } from 'lib/errorHandling';
import { handleApprovalRedirect } from 'lib/hooks/api/approvals';
import yup from 'lib/yupExtended';
import Editor from 'components/common/Editor/Editor';
import Select from 'components/common/Select/Select';
import { FormError } from 'components/common/Input/Input.styled';
import Input from 'components/common/Input/Input';
import { Button } from 'components/common/Button/Button';
import Heading from 'components/common/heading/Heading.styled';
import {
  useConnects,
  useConnectorPlugins,
  useValidateConnectorPluginConfig,
  useCreateConnector,
} from 'lib/hooks/api/kafkaConnect';
import { Connect } from 'generated-sources';
import ResourcePageHeading from 'components/common/ResourcePageHeading/ResourcePageHeading';
import DynamicForm, { ConnectorPluginConfigItem } from './DynamicForm';
import * as S from './New.styled';

const validationSchema = yup.object().shape({
  name: yup.string().required(),
  config: yup.string().required().isJsonObject(),
});

interface FormValues {
  connectName: Connect['name'];
  name: string;
  config: string;
}

const New: React.FC = () => {
  const { clusterName } = useAppParams<ClusterNameRoute>();
  const navigate = useNavigate();

  const { data: connects = [] } = useConnects(clusterName);
  const mutation = useCreateConnector(clusterName);
  const validateMutation = useValidateConnectorPluginConfig(clusterName);

  const [selectedConnect, setSelectedConnect] = useState<string>('');
  const [selectedPlugin, setSelectedPlugin] = useState<string>('');
  const [pluginConfigs, setPluginConfigs] = useState<ConnectorPluginConfigItem[]>([]);
  const [formConfigValues, setFormConfigValues] = useState<Record<string, string | number | boolean>>({});
  const [viewMode, setViewMode] = useState<'form' | 'json'>('form');

  const methods = useForm<FormValues>({
    mode: 'all',
    resolver: yupResolver(validationSchema),
    defaultValues: {
      connectName: '',
      name: '',
      config: '',
    },
  });

  const {
    handleSubmit,
    control,
    formState: { isDirty, isSubmitting, isValid, errors },
    getValues,
    setValue,
  } = methods;

  // Initialize connectName from connects list
  useEffect(() => {
    if (connects && connects.length > 0 && !selectedConnect) {
      const initialConnect = connects[0].name;
      setSelectedConnect(initialConnect);
      setValue('connectName', initialConnect);
    }
  }, [connects, selectedConnect, setValue]);

  // Query plugins for the selected connect cluster
  const { data: plugins = [], isLoading: isPluginsLoading } = useConnectorPlugins(
    clusterName,
    selectedConnect
  );

  const handleConnectChange = (newConnect: string) => {
    setSelectedConnect(newConnect);
    setValue('connectName', newConnect);
    setSelectedPlugin('');
    setPluginConfigs([]);
    setFormConfigValues({});
    setValue('config', '');
  };

  const handlePluginChange = async (pluginClass: string) => {
    setSelectedPlugin(pluginClass);
    try {
      const initialBody = { 'connector.class': pluginClass };
      const validationResponse = await validateMutation.mutateAsync({
        connectName: selectedConnect,
        pluginName: pluginClass,
        requestBody: initialBody,
      });

      if (validationResponse && validationResponse.configs) {
        setPluginConfigs(validationResponse.configs as ConnectorPluginConfigItem[]);

        // Extract default values from definitions
        const initialValues: Record<string, string | number | boolean> = {
          'connector.class': pluginClass,
        };

        validationResponse.configs.forEach((cfg) => {
          if (cfg.definition?.name && cfg.definition?.defaultValue !== undefined) {
            initialValues[cfg.definition.name] = cfg.definition.defaultValue;
          }
        });

        setFormConfigValues(initialValues);
        setValue('config', JSON.stringify(initialValues, null, 2), { shouldDirty: true, shouldValidate: true });
        setViewMode('form');
      }
    } catch {
      // Fallback: populate basic config
      const fallbackConfig = { 'connector.class': pluginClass };
      setFormConfigValues(fallbackConfig);
      setValue('config', JSON.stringify(fallbackConfig, null, 2), { shouldDirty: true, shouldValidate: true });
    }
  };

  const handleFormFieldChange = (key: string, value: string | number | boolean) => {
    const updatedValues = {
      ...formConfigValues,
      [key]: value,
    };
    setFormConfigValues(updatedValues);
    setValue('config', JSON.stringify(updatedValues, null, 2), { shouldDirty: true, shouldValidate: true });
  };

  const handleSwitchMode = (mode: 'form' | 'json') => {
    if (mode === 'form') {
      try {
        const rawJson = getValues().config;
        if (rawJson) {
          const parsed = JSON.parse(rawJson);
          if (typeof parsed === 'object' && parsed !== null) {
            setFormConfigValues(parsed);
          }
        }
      } catch {
        // invalid JSON; keep existing form values
      }
    } else if (mode === 'json') {
      if (Object.keys(formConfigValues).length > 0) {
        setValue('config', JSON.stringify(formConfigValues, null, 2), {
          shouldDirty: true,
          shouldValidate: true,
        });
      }
      setTimeout(() => {
        window.dispatchEvent(new Event('resize'));
      }, 50);
    }
    setViewMode(mode);
  };

  const onSubmit = async (values: FormValues) => {
    try {
      const rawConfig = values.config?.trim() || '';
      const baseConfig =
        viewMode === 'form' && Object.keys(formConfigValues).length > 0
          ? formConfigValues
          : rawConfig
          ? JSON.parse(rawConfig)
          : {};

      const finalConfig = {
        ...baseConfig,
        'connector.class': selectedPlugin || baseConfig['connector.class'],
      };

      const connector: any = await mutation.createResource({
        connectName: values.connectName || selectedConnect,
        newConnector: {
          name: values.name,
          config: finalConfig,
        },
      });

      if (
        handleApprovalRedirect(
          connector,
          clusterName,
          navigate,
          'Connector creation request submitted to approval queue.'
        )
      ) {
        return;
      }

      if (connector?.connect && connector?.name) {
        navigate(
          clusterConnectConnectorPath(
            clusterName,
            connector.connect,
            connector.name
          )
        );
      }
    } catch (e: any) {
      if (
        handleApprovalRedirect(
          e,
          clusterName,
          navigate,
          'Connector creation request submitted to approval queue.'
        )
      ) {
        return;
      }
    }
  };

  const connectOptions = connects.map(({ name: connectName }) => ({
    value: connectName,
    label: connectName,
  }));

  const pluginOptions = plugins.map((plugin) => {
    const className = plugin._class || (plugin as any).class || '';
    return {
      value: className,
      label: className,
    };
  });

  return (
    <FormProvider {...methods}>
      <ResourcePageHeading
        text="Create new connector"
        backTo={clusterConnectorsPath(clusterName)}
        backText="Connectors"
      />
      <S.NewConnectFormStyled
        onSubmit={handleSubmit(onSubmit)}
        aria-label="Create connect form"
      >
        <S.Filed $hidden={connects?.length <= 1}>
          <Heading level={3}>Connect *</Heading>
          <Select
            selectSize="M"
            name="connectName"
            disabled={isSubmitting}
            onChange={(val: any) => {
              const selectedVal = typeof val === 'object' && val !== null ? val.value : val;
              handleConnectChange(selectedVal);
            }}
            value={selectedConnect}
            minWidth="100%"
            options={connectOptions}
          />
          <FormError>
            <ErrorMessage errors={errors} name="connectName" />
          </FormError>
        </S.Filed>

        <div>
          <Heading level={3}>Connector Plugin *</Heading>
          <Select
            selectSize="M"
            name="pluginName"
            disabled={isSubmitting || isPluginsLoading || pluginOptions.length === 0}
            placeholder={isPluginsLoading ? 'Loading plugins...' : 'Select connector plugin'}
            onChange={(val: any) => {
              const selectedVal = typeof val === 'object' && val !== null ? val.value : val;
              handlePluginChange(selectedVal);
            }}
            value={selectedPlugin}
            minWidth="100%"
            options={pluginOptions}
          />
        </div>

        <div>
          <Heading level={3}>Name *</Heading>
          <Input
            inputSize="M"
            placeholder="Connector Name"
            name="name"
            autoFocus
            autoComplete="off"
            disabled={isSubmitting}
          />
          <FormError>
            <ErrorMessage errors={errors} name="name" />
          </FormError>
        </div>

        <div>
          <S.ModeSwitcherWrapper>
            <Heading level={3}>Configuration</Heading>
            <S.ModeButtonGroup>
              <S.ModeButton
                type="button"
                $active={viewMode === 'form'}
                onClick={() => handleSwitchMode('form')}
              >
                Form Builder
              </S.ModeButton>
              <S.ModeButton
                type="button"
                $active={viewMode === 'json'}
                onClick={() => handleSwitchMode('json')}
              >
                Raw JSON
              </S.ModeButton>
            </S.ModeButtonGroup>
          </S.ModeSwitcherWrapper>

          {pluginConfigs.length > 0 && (
            <div style={{ display: viewMode === 'form' ? 'block' : 'none' }}>
              <DynamicForm
                configs={pluginConfigs}
                values={formConfigValues}
                onChange={handleFormFieldChange}
                disabled={isSubmitting}
              />
            </div>
          )}

          <div
            style={{
              display:
                viewMode === 'json' || pluginConfigs.length === 0
                  ? 'block'
                  : 'none',
            }}
          >
            <Controller
              control={control}
              name="config"
              render={({ field }) => (
                <Editor {...field} readOnly={isSubmitting} ref={null} />
              )}
            />
          </div>

          <FormError>
            <ErrorMessage errors={errors} name="config" />
          </FormError>
        </div>

        <Button
          buttonSize="M"
          buttonType="primary"
          type="submit"
          disabled={!isValid || isSubmitting || !isDirty}
        >
          Submit
        </Button>
      </S.NewConnectFormStyled>
    </FormProvider>
  );
};

export default New;
