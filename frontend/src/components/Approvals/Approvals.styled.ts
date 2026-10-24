import styled from 'styled-components';

export const Wrapper = styled.div`
  padding: 16px 24px;
`;

export const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
`;

export const Title = styled.h1`
  font-size: 20px;
  font-weight: 500;
  color: ${({ theme }) => theme.heading.base.color};
  margin: 0;
`;

export const Subtitle = styled.div`
  font-size: 13px;
  color: ${({ theme }) => theme.table.th.color.normal};
  margin-top: 4px;
`;

export const HeaderActions = styled.div`
  display: flex;
  gap: 8px;
  align-items: center;
`;

export const Filters = styled.div`
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
`;

export const FilterItem = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;

  label {
    font-size: 13px;
    font-weight: 500;
    color: ${({ theme }) => theme.table.th.color.normal};
  }

  select {
    padding: 6px 10px;
    border-radius: 4px;
    border: 1px solid ${({ theme }) => theme.select.borderColor.normal};
    background-color: ${({ theme }) => theme.select.backgroundColor.normal};
    color: ${({ theme }) => theme.select.color.normal};
    font-size: 13px;
    outline: none;

    &:focus {
      border-color: ${({ theme }) => theme.select.borderColor.active};
    }
  }
`;

export const TableContainer = styled.div`
  background: ${({ theme }) => theme.default.backgroundColor};
  border: 1px solid ${({ theme }) => theme.table.td.borderTop};
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
`;

export const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;

  th {
    padding: 10px 16px;
    text-align: left;
    font-weight: 600;
    font-size: 12px;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: ${({ theme }) => theme.table.th.color.normal};
    background-color: ${({ theme }) => theme.table.th.backgroundColor.normal};
    border-bottom: 1px solid ${({ theme }) => theme.table.td.borderTop};
  }

  td {
    padding: 12px 16px;
    color: ${({ theme }) => theme.table.td.color.normal};
    border-bottom: 1px solid ${({ theme }) => theme.table.td.borderTop};
    vertical-align: middle;
  }

  tbody tr:hover {
    background-color: ${({ theme }) => theme.table.tr.backgroundColor.hover};
  }
`;

export const EmptyState = styled.div`
  padding: 40px 16px;
  text-align: center;
  color: ${({ theme }) => theme.table.td.color.normal};
  font-size: 14px;
`;

export const ActionGroup = styled.div`
  display: flex;
  gap: 6px;
  align-items: center;
`;

export const DetailsContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

export const InfoGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  background-color: ${({ theme }) => theme.modal.backgroundColor};
  border: 1px solid ${({ theme }) => theme.table.td.borderTop};
  padding: 12px 16px;
  border-radius: 6px;
`;

export const InfoItem = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;

  span:first-child {
    font-size: 12px;
    font-weight: 500;
    color: ${({ theme }) => theme.table.th.color.normal};
  }

  span:last-child:not([role="status"]) {
    font-size: 14px;
    color: ${({ theme }) => theme.heading.base.color};
    font-weight: 500;
  }
`;

export const StatusBadge = styled.span.attrs({ role: 'status' })<{ $status: string }>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  width: max-content;
  line-height: 1.3;

  ${({ $status }) => {
    switch ($status) {
      case 'PENDING':
        return `
          background-color: rgba(234, 179, 8, 0.16);
          border: 1px solid rgba(234, 179, 8, 0.45);
          color: #eab308 !important;
        `;
      case 'EXECUTED':
      case 'APPROVED':
        return `
          background-color: rgba(34, 197, 94, 0.16);
          border: 1px solid rgba(34, 197, 94, 0.45);
          color: #22c55e !important;
        `;
      case 'REJECTED':
      case 'EXECUTION_FAILED':
        return `
          background-color: rgba(239, 68, 68, 0.16);
          border: 1px solid rgba(239, 68, 68, 0.45);
          color: #ef4444 !important;
        `;
      default:
        return `
          background-color: rgba(156, 163, 175, 0.16);
          border: 1px solid rgba(156, 163, 175, 0.45);
          color: #9ca3af !important;
        `;
    }
  }}
`;

export const DiffContainer = styled.div`
  margin-top: 12px;
  border: 1px solid ${({ theme }) => theme.table.td.borderTop};
  border-radius: 6px;
  overflow: hidden;
`;

export const DiffHeader = styled.div`
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  background-color: ${({ theme }) => theme.table.th.backgroundColor.normal};
  font-size: 12px;
  font-weight: 600;
  color: ${({ theme }) => theme.table.th.color.normal};
  border-bottom: 1px solid ${({ theme }) => theme.table.td.borderTop};
`;

export const Textarea = styled.textarea`
  width: 100%;
  padding: 10px;
  border-radius: 4px;
  border: 1px solid ${({ theme }) => theme.input.borderColor.normal};
  background-color: ${({ theme }) => theme.input.backgroundColor.normal};
  color: ${({ theme }) => theme.input.color.normal};
  font-size: 13px;
  resize: vertical;
  min-height: 80px;
  outline: none;

  &:focus {
    border-color: ${({ theme }) => theme.input.borderColor.focus};
  }
`;

export const SettingCheckboxLabel = styled.label`
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  color: ${({ theme }) => theme.heading.base.color};
  user-select: none;

  input[type='checkbox'] {
    width: 16px;
    height: 16px;
    cursor: pointer;
    accent-color: #22c55e;
  }
`;

export const SettingDescription = styled.div`
  font-size: 12px;
  color: ${({ theme }) => theme.table.th.color.normal};
  margin-left: 24px;
  margin-top: 2px;
`;

export const SettingInputLabel = styled.label`
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: ${({ theme }) => theme.heading.base.color};
  margin-bottom: 6px;
`;

export const SettingInput = styled.input`
  width: 100%;
  padding: 8px 12px;
  border-radius: 4px;
  border: 1px solid ${({ theme }) => theme.input.borderColor.normal};
  background-color: ${({ theme }) => theme.input.backgroundColor.normal};
  color: ${({ theme }) => theme.input.color.normal};
  font-size: 13px;
  box-sizing: border-box;
  outline: none;

  &:focus {
    border-color: ${({ theme }) => theme.input.borderColor.focus};
  }
`;
