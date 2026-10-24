import React, { useState } from 'react';
import * as InputStyled from 'components/common/Input/Input.styled';
import Switch from 'components/common/Switch/Switch';
import Select from 'components/common/Select/Select';
import Tooltip from 'components/common/Tooltip/Tooltip';
import { FormError } from 'components/common/Input/Input.styled';
import * as S from './New.styled';

export interface ConnectorPluginConfigDefinition {
  name?: string;
  type?: string;
  required?: boolean;
  defaultValue?: string;
  importance?: string;
  documentation?: string;
  group?: string;
  width?: string;
  displayName?: string;
}

export interface ConnectorPluginConfigValue {
  name?: string;
  value?: string;
  recommendedValues?: string[];
  errors?: string[];
  visible?: boolean;
}

export interface ConnectorPluginConfigItem {
  definition?: ConnectorPluginConfigDefinition;
  value?: ConnectorPluginConfigValue;
}

interface DynamicFormProps {
  configs: ConnectorPluginConfigItem[];
  values: Record<string, string | number | boolean>;
  onChange: (key: string, value: string | number | boolean) => void;
  disabled?: boolean;
}

export const DynamicForm: React.FC<DynamicFormProps> = ({
  configs,
  values,
  onChange,
  disabled = false,
}) => {
  const [showSecrets, setShowSecrets] = useState<Record<string, boolean>>({});

  const toggleSecret = (key: string) => {
    setShowSecrets((prev) => ({
      ...prev,
      [key]: !prev[key],
    }));
  };

  // Filter out 'name' and 'connector.class' because New.tsx has dedicated top-level inputs for them
  const filteredConfigs = React.useMemo(() => {
    return configs.filter(
      (item) =>
        item.definition?.name !== 'name' &&
        item.definition?.name !== 'connector.class'
    );
  }, [configs]);

  // Group configs by group name
  const groupedConfigs = React.useMemo(() => {
    const groups: Record<string, ConnectorPluginConfigItem[]> = {};

    filteredConfigs.forEach((item) => {
      const groupName = item.definition?.group || 'General Configuration';
      if (!groups[groupName]) {
        groups[groupName] = [];
      }
      groups[groupName].push(item);
    });

    return groups;
  }, [filteredConfigs]);

  const [openGroups, setOpenGroups] = useState<Record<string, boolean>>(() => {
    // Default open all groups initially
    const initial: Record<string, boolean> = {};
    filteredConfigs.forEach((item) => {
      const groupName = item.definition?.group || 'General Configuration';
      initial[groupName] = true;
    });
    return initial;
  });

  const toggleGroup = (groupName: string) => {
    setOpenGroups((prev) => ({
      ...prev,
      [groupName]: !prev[groupName],
    }));
  };

  const renderField = (item: ConnectorPluginConfigItem) => {
    const def = item.definition;
    if (!def || !def.name || def.name === 'name' || def.name === 'connector.class') return null;

    const key = def.name;
    const type = def.type?.toUpperCase() || 'STRING';
    const currentValue = values[key] !== undefined ? values[key] : (def.defaultValue ?? '');
    const errors = item.value?.errors;
    const recommended = item.value?.recommendedValues || [];

    const isPassword = type === 'PASSWORD' || key.toLowerCase().includes('password') || key.toLowerCase().includes('secret');

    return (
      <S.FieldWrapper key={key}>
        <S.LabelRow>
          <span>{def.displayName || def.name}</span>
          {def.required && <S.RequiredMark>*</S.RequiredMark>}
          {def.documentation && (
            <Tooltip
              content={def.documentation}
              value={<S.InfoBadge>?</S.InfoBadge>}
            />
          )}
        </S.LabelRow>

        {isPassword ? (
          <S.PasswordWrapper>
            <InputStyled.Input
              inputSize="M"
              search={false}
              type={showSecrets[key] ? 'text' : 'password'}
              name={key}
              autoComplete="new-password"
              placeholder={def.defaultValue ? `Default: ${def.defaultValue}` : 'Enter password / secret'}
              value={String(currentValue)}
              disabled={disabled}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => onChange(key, e.target.value)}
            />
            <S.PasswordToggleBtn
              type="button"
              aria-label={showSecrets[key] ? 'Hide password' : 'Show password'}
              onClick={() => toggleSecret(key)}
            >
              {showSecrets[key] ? 'Hide' : 'Show'}
            </S.PasswordToggleBtn>
          </S.PasswordWrapper>
        ) : type === 'BOOLEAN' ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Switch
              name={key}
              checked={String(currentValue).toLowerCase() === 'true' || Boolean(currentValue)}
              onChange={() => {
                const currentBool = String(currentValue).toLowerCase() === 'true' || Boolean(currentValue);
                onChange(key, !currentBool);
              }}
            />
            <S.ToggleText $checked={String(currentValue).toLowerCase() === 'true' || Boolean(currentValue)}>
              {String(currentValue).toLowerCase() === 'true' ? 'true' : 'false'}
            </S.ToggleText>
          </div>
        ) : type === 'INT' || type === 'LONG' || type === 'SHORT' || type === 'DOUBLE' ? (
          <InputStyled.Input
            inputSize="M"
            search={false}
            type="number"
            step={type === 'DOUBLE' ? 'any' : '1'}
            name={key}
            placeholder={def.defaultValue ? `Default: ${def.defaultValue}` : undefined}
            value={currentValue !== undefined && currentValue !== null ? String(currentValue) : ''}
            disabled={disabled}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
              const val = e.target.value;
              onChange(key, val === '' ? '' : Number(val));
            }}
          />
        ) : type === 'LIST' ? (
          <InputStyled.Input
            inputSize="M"
            search={false}
            type="text"
            name={key}
            placeholder={def.defaultValue ? `Default: ${def.defaultValue}` : 'Comma-separated values (e.g. val1, val2)'}
            value={currentValue !== undefined && currentValue !== null ? String(currentValue) : ''}
            disabled={disabled}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => onChange(key, e.target.value)}
          />
        ) : recommended.length > 0 ? (
          <Select
            selectSize="M"
            name={key}
            disabled={disabled}
            value={String(currentValue)}
            options={recommended.map((v) => ({ value: String(v), label: String(v) }))}
            onChange={(opt: any) => {
              const val = typeof opt === 'object' && opt !== null ? opt.value : opt;
              onChange(key, val);
            }}
          />
        ) : (
          <InputStyled.Input
            inputSize="M"
            search={false}
            type="text"
            name={key}
            placeholder={def.defaultValue ? `Default: ${def.defaultValue}` : undefined}
            value={currentValue !== undefined && currentValue !== null ? String(currentValue) : ''}
            disabled={disabled}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => onChange(key, e.target.value)}
          />
        )}

        {def.documentation && !def.displayName && (
          <S.HelperText>{def.documentation}</S.HelperText>
        )}

        {errors && errors.length > 0 && (
          <FormError>
            {errors.map((err, idx) => (
              <div key={idx}>{err}</div>
            ))}
          </FormError>
        )}
      </S.FieldWrapper>
    );
  };

  return (
    <div>
      {Object.entries(groupedConfigs).map(([groupName, items]) => {
        const isOpen = openGroups[groupName] !== false;
        return (
          <S.GroupCard key={groupName}>
            <S.GroupHeader onClick={() => toggleGroup(groupName)}>
              <span>{groupName} ({items.length})</span>
              <span>{isOpen ? '▲' : '▼'}</span>
            </S.GroupHeader>
            {isOpen && (
              <S.GroupContent>
                {items.map(renderField)}
              </S.GroupContent>
            )}
          </S.GroupCard>
        );
      })}
    </div>
  );
};

export default DynamicForm;
