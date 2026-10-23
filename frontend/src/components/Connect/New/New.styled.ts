import styled, { css } from 'styled-components';

export const NewConnectFormStyled = styled.form`
  padding: 0 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;

  & > button:last-child {
    align-self: flex-start;
  }
`;

export const Filed = styled.div<{ $hidden?: boolean }>(
  ({ $hidden }) =>
    $hidden &&
    css`
      display: none;
    `
);

export const ModeSwitcherWrapper = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
`;

export const ModeButtonGroup = styled.div`
  display: inline-flex;
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid ${({ theme }) => theme.select.borderColor.normal};
`;

export const ModeButton = styled.button<{ $active: boolean }>`
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  background-color: ${({ $active, theme }) =>
    $active ? theme.button.primary.backgroundColor.normal : theme.select.backgroundColor.normal};
  color: ${({ $active, theme }) =>
    $active ? theme.button.primary.color.normal : theme.heading.color.normal};
  transition: background-color 0.15s ease-in-out;

  &:hover {
    opacity: 0.9;
  }
`;

export const GroupCard = styled.div`
  border: 1px solid ${({ theme }) => theme.select.borderColor.normal};
  border-radius: 6px;
  background-color: ${({ theme }) => theme.select.backgroundColor.normal};
  margin-bottom: 16px;
  overflow: hidden;
`;

export const GroupHeader = styled.div`
  padding: 12px 16px;
  background-color: ${({ theme }) => theme.table.th.backgroundColor.normal};
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 14px;
  color: ${({ theme }) => theme.heading.color.normal};
  user-select: none;

  &:hover {
    opacity: 0.95;
  }
`;

export const GroupContent = styled.div`
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

export const FieldWrapper = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
`;

export const LabelRow = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: ${({ theme }) => theme.heading.color.normal};
`;

export const RequiredMark = styled.span`
  color: ${({ theme }) => theme.alert.color.error};
`;

export const HelperText = styled.span`
  font-size: 12px;
  color: ${({ theme }) => theme.table.th.color.normal};
  line-height: 1.3;
`;

export const InfoBadge = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 15px;
  height: 15px;
  border-radius: 50%;
  font-size: 10px;
  font-weight: bold;
  background-color: ${({ theme }) => theme.table.th.backgroundColor.normal};
  color: ${({ theme }) => theme.table.th.color.normal};
  cursor: help;
`;
