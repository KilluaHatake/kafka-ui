import React from 'react';
import { render } from 'lib/testHelpers';
import { screen, fireEvent } from '@testing-library/react';
import DynamicForm, { ConnectorPluginConfigItem } from '../DynamicForm';

describe('DynamicForm', () => {
  const mockConfigs: ConnectorPluginConfigItem[] = [
    {
      definition: {
        name: 'connector.class',
        displayName: 'Connector Class',
        type: 'STRING',
        required: true,
        group: 'Common',
        documentation: 'Class name of connector',
      },
      value: {
        name: 'connector.class',
        value: 'org.example.MyConnector',
      },
    },
    {
      definition: {
        name: 'connection.password',
        displayName: 'Connection Password',
        type: 'PASSWORD',
        required: true,
        group: 'Security',
        documentation: 'Password for DB connection',
      },
      value: {
        name: 'connection.password',
        value: '',
      },
    },
    {
      definition: {
        name: 'tasks.max',
        displayName: 'Max Tasks',
        type: 'INT',
        required: false,
        defaultValue: '1',
        group: 'Common',
        documentation: 'Maximum number of tasks',
      },
      value: {
        name: 'tasks.max',
        value: '1',
      },
    },
  ];

  it('renders groups and fields with appropriate types and masking', () => {
    const handleChange = jest.fn();
    render(
      <DynamicForm
        configs={mockConfigs}
        values={{ 'connector.class': 'org.example.MyConnector', 'tasks.max': 1 }}
        onChange={handleChange}
      />
    );

    // Group headers should render
    expect(screen.getByText(/Common/i)).toBeInTheDocument();
    expect(screen.getByText(/Security/i)).toBeInTheDocument();

    // Password field should be type="password"
    const passwordInput = screen.getByPlaceholderText('Enter password / secret');
    expect(passwordInput).toBeInTheDocument();
    expect(passwordInput).toHaveAttribute('type', 'password');

    // Number field should have type="number"
    const maxTasksInput = screen.getByDisplayValue('1');
    expect(maxTasksInput).toHaveAttribute('type', 'number');

    // Changing password calls onChange
    fireEvent.change(passwordInput, { target: { value: 'supersecret' } });
    expect(handleChange).toHaveBeenCalledWith('connection.password', 'supersecret');
  });

  it('renders boolean fields with theme-colored toggle text', () => {
    const handleChange = jest.fn();
    const booleanConfigs: ConnectorPluginConfigItem[] = [
      {
        definition: {
          name: 'enable.logging',
          displayName: 'Enable Logging',
          type: 'BOOLEAN',
          required: false,
          defaultValue: 'true',
          group: 'Common',
        },
        value: {
          name: 'enable.logging',
          value: 'true',
        },
      },
    ];

    render(
      <DynamicForm
        configs={booleanConfigs}
        values={{ 'enable.logging': true }}
        onChange={handleChange}
      />
    );

    expect(screen.getByText('true')).toBeInTheDocument();
  });
});
