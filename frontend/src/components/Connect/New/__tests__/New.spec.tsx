import React from 'react';
import { render, WithRoute } from 'lib/testHelpers';
import {
  clusterConnectConnectorPath,
  clusterConnectorNewPath,
} from 'lib/paths';
import New from 'components/Connect/New/New';
import { connects, connector } from 'lib/fixtures/kafkaConnect';
import { fireEvent, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ControllerRenderProps } from 'react-hook-form';
import {
  useConnects,
  useCreateConnector,
  useConnectorPlugins,
  useValidateConnectorPluginConfig,
} from 'lib/hooks/api/kafkaConnect';
import { showAlert } from 'lib/errorHandling';

jest.mock('lib/errorHandling', () => ({
  ...jest.requireActual('lib/errorHandling'),
  showAlert: jest.fn(),
}));

jest.mock(
  'components/common/Editor/Editor',
  () => (props: ControllerRenderProps) => {
    return <textarea {...props} placeholder="json" />;
  }
);

const mockHistoryPush = jest.fn();
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockHistoryPush,
}));

jest.mock('lib/hooks/api/kafkaConnect', () => ({
  useConnects: jest.fn(),
  useCreateConnector: jest.fn(),
  useConnectorPlugins: jest.fn(),
  useValidateConnectorPluginConfig: jest.fn(),
}));

describe('New', () => {
  const clusterName = 'my-cluster';
  const simulateFormSubmit = async () => {
    await userEvent.type(
      screen.getByPlaceholderText('Connector Name'),
      'my-connector'
    );
    await userEvent.type(
      screen.getByPlaceholderText('json'),
      '{"class":"MyClass"}'.replace(/[{[]/g, '$&$&')
    );

    expect(screen.getByPlaceholderText('json')).toHaveValue(
      '{"class":"MyClass"}'
    );
    await act(() => {
      fireEvent.submit(screen.getByRole('form'));
    });
  };

  const renderComponent = () =>
    render(
      <WithRoute path={clusterConnectorNewPath()}>
        <New />
      </WithRoute>,
      { initialEntries: [clusterConnectorNewPath(clusterName)] }
    );

  beforeEach(() => {
    (useConnects as jest.Mock).mockImplementation(() => ({
      data: connects,
    }));
    (useConnectorPlugins as jest.Mock).mockImplementation(() => ({
      data: [],
      isLoading: false,
    }));
    (useValidateConnectorPluginConfig as jest.Mock).mockImplementation(() => ({
      mutateAsync: jest.fn().mockResolvedValue({ configs: [] }),
    }));
  });

  it('calls createConnector on form submit and redirects to the list page on success', async () => {
    const createConnectorMock = jest.fn(() => {
      return Promise.resolve(connector);
    });
    (useCreateConnector as jest.Mock).mockImplementation(() => ({
      createResource: createConnectorMock,
    }));
    renderComponent();
    await simulateFormSubmit();
    expect(createConnectorMock).toHaveBeenCalledTimes(1);
    expect(mockHistoryPush).toHaveBeenCalledTimes(1);
    expect(mockHistoryPush).toHaveBeenCalledWith(
      clusterConnectConnectorPath(clusterName, connects[0].name, connector.name)
    );
  });

  it('does not redirect to connector details view on unsuccessful submit', async () => {
    const createConnectorMock = jest.fn(() => {
      return Promise.resolve();
    });
    (useCreateConnector as jest.Mock).mockImplementation(() => ({
      createResource: createConnectorMock,
    }));
    renderComponent();
    await simulateFormSubmit();
    expect(mockHistoryPush).not.toHaveBeenCalled();
  });

  describe('Form Builder and Raw JSON synchronization', () => {
    const mockPlugins = [
      { class: 'org.apache.kafka.connect.file.FileStreamSinkConnector' },
    ];
    const mockValidation = {
      configs: [
        {
          definition: {
            name: 'file',
            displayName: 'Destination File',
            type: 'STRING',
            group: 'Common',
            defaultValue: 'default.txt',
          },
        },
        {
          definition: {
            name: 'topics',
            displayName: 'Topics',
            type: 'STRING',
            group: 'Common',
          },
        },
      ],
    };

    beforeEach(() => {
      (useConnectorPlugins as jest.Mock).mockImplementation(() => ({
        data: mockPlugins,
        isLoading: false,
      }));
      (useValidateConnectorPluginConfig as jest.Mock).mockImplementation(() => ({
        mutateAsync: jest.fn().mockResolvedValue(mockValidation),
      }));
    });

    it('populates raw JSON editor with form values when switching to raw JSON', async () => {
      renderComponent();

      await userEvent.type(
        screen.getByPlaceholderText('Connector Name'),
        'my-sink-connector'
      );

      await act(async () => {
        fireEvent.click(screen.getByText('Select connector plugin'));
      });
      await act(async () => {
        fireEvent.click(
          screen.getByText('org.apache.kafka.connect.file.FileStreamSinkConnector')
        );
      });

      expect(screen.getByText(/Common/i)).toBeInTheDocument();

      const fileInput = screen.getByDisplayValue('default.txt');
      expect(fileInput).toBeInTheDocument();

      const inputs = screen.getAllByRole('textbox');
      const emptyInput = inputs.find(
        (input) => (input as HTMLInputElement).value === ''
      );
      if (emptyInput) {
        fireEvent.change(emptyInput, { target: { value: 'orders-topic' } });
      }

      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: 'Raw JSON' }));
      });

      const jsonTextarea = screen.getByPlaceholderText('json') as HTMLTextAreaElement;
      expect(jsonTextarea).toBeInTheDocument();
      const parsedJson = JSON.parse(jsonTextarea.value);
      expect(parsedJson).toEqual(
        expect.objectContaining({
          name: 'my-sink-connector',
          'connector.class': 'org.apache.kafka.connect.file.FileStreamSinkConnector',
          file: 'default.txt',
          topics: 'orders-topic',
        })
      );
    });

    it('allows editing raw JSON and adding custom configurations not in connector definition', async () => {
      const createConnectorMock = jest.fn(() => Promise.resolve(connector));
      (useCreateConnector as jest.Mock).mockImplementation(() => ({
        createResource: createConnectorMock,
      }));

      renderComponent();

      await userEvent.type(
        screen.getByPlaceholderText('Connector Name'),
        'custom-connector'
      );

      await act(async () => {
        fireEvent.click(screen.getByText('Select connector plugin'));
      });
      await act(async () => {
        fireEvent.click(
          screen.getByText('org.apache.kafka.connect.file.FileStreamSinkConnector')
        );
      });

      // Switch to Raw JSON
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: 'Raw JSON' }));
      });

      const jsonTextarea = screen.getByPlaceholderText('json');
      const customConfig = {
        name: 'custom-connector',
        'connector.class': 'org.apache.kafka.connect.file.FileStreamSinkConnector',
        file: '/custom/path/file.txt',
        'transforms': 'unwrap',
        'transforms.unwrap.type': 'io.debezium.transforms.ExtractNewRecordState',
        'my.extra.unsupported.setting': 'custom-val',
      };

      fireEvent.change(jsonTextarea, {
        target: { value: JSON.stringify(customConfig, null, 2) },
      });

      await act(async () => {
        fireEvent.submit(screen.getByRole('form'));
      });

      expect(createConnectorMock).toHaveBeenCalledWith(
        expect.objectContaining({
          newConnector: expect.objectContaining({
            name: 'custom-connector',
            config: expect.objectContaining({
              file: '/custom/path/file.txt',
              transforms: 'unwrap',
              'transforms.unwrap.type': 'io.debezium.transforms.ExtractNewRecordState',
              'my.extra.unsupported.setting': 'custom-val',
            }),
          }),
        })
      );
    });

    it('preserves custom configs when toggling between raw JSON and form builder', async () => {
      const { container } = renderComponent();

      await act(async () => {
        fireEvent.click(screen.getByText('Select connector plugin'));
      });
      await act(async () => {
        fireEvent.click(
          screen.getByText('org.apache.kafka.connect.file.FileStreamSinkConnector')
        );
      });

      // Switch to Raw JSON
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: 'Raw JSON' }));
      });

      const jsonTextarea = screen.getByPlaceholderText('json');
      const customConfig = {
        'connector.class': 'org.apache.kafka.connect.file.FileStreamSinkConnector',
        file: 'edited-in-json.txt',
        'custom.plugin.unregistered.key': 'secret-prop',
      };

      fireEvent.change(jsonTextarea, {
        target: { value: JSON.stringify(customConfig, null, 2) },
      });

      // Switch back to Form Builder
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: 'Form Builder' }));
      });

      // Form builder should show the edited file value
      expect(screen.getByDisplayValue('edited-in-json.txt')).toBeInTheDocument();

      // Edit another field (topics) in the form builder
      const topicsInput = container.querySelector('input[name="topics"]') as HTMLInputElement;
      expect(topicsInput).toBeInTheDocument();
      fireEvent.change(topicsInput, { target: { value: 'extra-topic' } });

      // Switch back to Raw JSON
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: 'Raw JSON' }));
      });

      const updatedJson = JSON.parse(
        (screen.getByPlaceholderText('json') as HTMLTextAreaElement).value
      );

      // Must have preserved the custom key AND the updated values!
      expect(updatedJson['custom.plugin.unregistered.key']).toBe('secret-prop');
      expect(updatedJson.file).toBe('edited-in-json.txt');
      expect(updatedJson.topics).toBe('extra-topic');
    });

    it('shows warning alert and prevents switching to form builder when raw JSON is invalid', async () => {
      renderComponent();

      await act(async () => {
        fireEvent.click(screen.getByText('Select connector plugin'));
      });
      await act(async () => {
        fireEvent.click(
          screen.getByText('org.apache.kafka.connect.file.FileStreamSinkConnector')
        );
      });

      // Switch to Raw JSON
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: 'Raw JSON' }));
      });

      const jsonTextarea = screen.getByPlaceholderText('json');
      fireEvent.change(jsonTextarea, {
        target: { value: '{ invalid: json syntax' },
      });

      // Try to switch back to Form Builder
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: 'Form Builder' }));
      });

      // Should show alert
      expect(showAlert).toHaveBeenCalledWith('warning', expect.objectContaining({
        title: 'Invalid JSON',
      }));

      // Editor should still be visible (stayed in raw JSON mode)
      expect(screen.getByPlaceholderText('json')).toBeInTheDocument();
    });
  });
});
