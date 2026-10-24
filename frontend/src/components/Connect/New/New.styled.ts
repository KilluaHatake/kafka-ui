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
    $active ? theme.button.primary.color.normal : theme.heading.base.color};
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
  color: ${({ theme }) => theme.heading.base.color};
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
  color: ${({ theme }) => theme.heading.base.color};
`;

export const RequiredMark = styled.span`
  color: #ef4444;
`;

export const HelperText = styled.span`
  font-size: 12px;
  color: ${({ theme }) => theme.table.th.color.normal};
  line-height: 1.3;
`;

export const ToggleText = styled.span<{ $checked?: boolean }>`
  font-size: 13px;
  font-weight: 600;
  color: ${({ $checked, theme }) =>
    $checked
      ? (theme.tag?.textColor?.green || '#22c55e')
      : theme.table.th.color.normal};
`;

export const InfoBadge = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  font-size: 10px;
  font-weight: bold;
  background-color: ${({ theme }) => theme.select.backgroundColor.normal};
  color: ${({ theme }) => theme.heading.base.color};
  border: 1px solid ${({ theme }) => theme.select.borderColor.normal};
  cursor: help;
  transition: all 0.2s ease;

  &:hover {
    color: ${({ theme }) => theme.button.primary.backgroundColor.normal};
    border-color: currentColor;
  }
`;

export const PasswordWrapper = styled.div`
  display: flex;
  align-items: center;
  position: relative;
  width: 100%;

  & > div {
    width: 100%;
  }

  & input {
    padding-right: 50px;
  }
`;

export const PasswordToggleBtn = styled.button`
  position: absolute;
  right: 10px;
  background: transparent;
  border: none;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  color: ${({ theme }) => theme.table.th.color.normal};
  padding: 4px 6px;
  border-radius: 4px;
  user-select: none;

  &:hover {
    opacity: 0.75;
  }
`;
