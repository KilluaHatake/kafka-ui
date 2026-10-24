import styled from 'styled-components';

export const MessageTooltip = styled.div`
  max-width: 380px;
  background-color: ${({ theme }) => theme.tooltip.bg};
  color: ${({ theme }) => theme.tooltip.text};
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 12px;
  line-height: 1.4;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
  white-space: normal;
  word-break: break-word;
  overflow-wrap: break-word;
  z-index: 1000;
  pointer-events: none;
`;

export const Wrapper = styled.div`
  display: flex;
  align-items: center;
`;
