import styled from 'styled-components';

export const PageContainer = styled.div`
  padding: 20px 24px;
  max-width: 960px;
  display: flex;
  flex-direction: column;
  gap: 24px;
`;

export const Card = styled.div`
  background: ${({ theme }) => theme.default.backgroundColor};
  border: 1px solid ${({ theme }) => theme.hr.backgroundColor};
  border-radius: 8px;
  padding: 20px 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
`;

export const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid ${({ theme }) => theme.hr.backgroundColor};
  padding-bottom: 14px;
  margin-bottom: 18px;
`;

export const CardHeaderLeft = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

export const CardTitle = styled.h2`
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: ${({ theme }) => theme.heading.base.color};
  display: flex;
  align-items: center;
  gap: 10px;
`;

export const CardSubtitle = styled.p`
  font-size: 13px;
  color: ${({ theme }) => theme.table.th.color.normal};
  margin: 0;
`;

export const FormGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 16px;

  &:last-child {
    margin-bottom: 0;
  }
`;

export const FormRow = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
`;

export const Label = styled.label`
  font-size: 13px;
  font-weight: 500;
  color: ${({ theme }) => theme.heading.base.color};
  display: flex;
  align-items: center;
  gap: 6px;
`;

export const HelpText = styled.span`
  font-size: 12px;
  color: ${({ theme }) => theme.table.th.color.normal};
`;

export const StatusBadge = styled.span<{ $active: boolean }>`
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  padding: 2px 10px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: max-content;
  line-height: 1.4;

  ${({ $active }) =>
    $active
      ? `
        background-color: rgba(34, 197, 94, 0.16);
        color: #22c55e !important;
        border: 1px solid rgba(34, 197, 94, 0.45);
      `
      : `
        background-color: rgba(156, 163, 175, 0.16);
        color: #9ca3af !important;
        border: 1px solid rgba(156, 163, 175, 0.45);
      `}
`;

export const TestActionsRow = styled.div`
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  padding-top: 14px;
  border-top: 1px dashed ${({ theme }) => theme.hr.backgroundColor};
  margin-top: 14px;
`;

export const SaveBar = styled.div`
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid ${({ theme }) => theme.hr.backgroundColor};
`;

export const CheckboxLabel = styled.label`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  color: ${({ theme }) => theme.heading.base.color};
  cursor: pointer;
  user-select: none;

  input[type='checkbox'] {
    width: 16px;
    height: 16px;
    cursor: pointer;
    accent-color: #22c55e;
  }
`;

export const PasswordWrapper = styled.div`
  display: flex;
  align-items: center;
  position: relative;
  width: 100%;

  & > input {
    padding-right: 48px;
  }
`;

export const PasswordToggleBtn = styled.button`
  position: absolute;
  right: 10px;
  background: transparent;
  border: none;
  font-size: 12px;
  cursor: pointer;
  color: ${({ theme }) => theme.table.th.color.normal};

  &:hover {
    color: ${({ theme }) => theme.heading.base.color};
  }
`;
