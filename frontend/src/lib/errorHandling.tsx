import React from 'react';
import Alert from 'components/common/Alert/Alert';
import toast, { ToastType } from 'react-hot-toast';
import { ErrorResponse } from 'generated-sources';

export interface ServerResponse {
  status?: number;
  statusText: string;
  url?: string;
  message?: ErrorResponse['message'];
}
export type ToastTypes = ToastType | 'warning';

export const getResponse = async (error: unknown): Promise<ServerResponse> => {
  if (error instanceof Response) {
    let body;

    try {
      body = await error.json();
    } catch {
      // do nothing
    }

    const apiError: ServerResponse = {
      status: error.status,
      statusText: error.statusText,
      url: error.url,
      message: body?.message,
    };

    return apiError;
  }

  return {
    statusText: 'Unknown error',
    message: error instanceof Error ? error.message : 'Unknown error',
  };
};

export const apiFetch = async <T,>(fn: () => Promise<T>): Promise<T> => {
  try {
    return await fn();
  } catch (e) {
    throw await getResponse(e);
  }
};

interface AlertOptions {
  id?: string;
  title?: string;
  message: React.ReactNode;
}

export const showAlert = (
  type: ToastTypes,
  { title, message, id }: AlertOptions
) => {
  toast.custom(
    (t) => (
      <Alert
        title={title || ''}
        type={type}
        message={message}
        onDissmiss={() => toast.remove(t.id)}
      />
    ),
    { id }
  );
};

export const showSuccessAlert = (options: AlertOptions) => {
  showAlert('success', {
    ...options,
    title: options.title || 'Success',
  });
};

export const showServerError = async (
  response: Response | ServerResponse | Error | unknown,
  options?: AlertOptions
) => {
  let body: Record<string, any> = {};
  const resp = response as any;

  if (resp && typeof resp.json === 'function') {
    try {
      const cloned = typeof resp.clone === 'function' ? resp.clone() : resp;
      body = await cloned.json();
    } catch {
      // do nothing
    }
  }

  const status = resp?.status || body?.status;
  const statusText = resp?.statusText || '';
  const message =
    body?.message ||
    resp?.message ||
    resp?.error ||
    (response instanceof Error ? response.message : '') ||
    'An error occurred';

  // Check if this was an approval required notification
  if (
    status === 202 ||
    body?.code === 4022 ||
    resp?.code === 4022 ||
    (typeof message === 'string' && message.toLowerCase().includes('approval'))
  ) {
    showAlert('warning', {
      id: resp?.url || 'approval-sent',
      title: 'Sent for Approval',
      message: message || 'Your change request has been submitted for approval.',
      ...options,
    });
    return;
  }

  if (status) {
    showAlert('error', {
      id: resp?.url,
      title: `${status} ${statusText}`.trim(),
      message,
      ...options,
    });
  } else {
    showAlert('error', {
      id: 'server-error',
      title: `Something went wrong`,
      message,
      ...options,
    });
  }
};
