import React, { useState } from 'react';
import {
  Placement,
  useFloating,
  useHover,
  useInteractions,
  offset,
  flip,
  shift,
  autoUpdate,
} from '@floating-ui/react';

import * as S from './Tooltip.styled';

interface TooltipProps {
  value: React.ReactNode;
  content: string;
  placement?: Placement;
  showTooltip?: boolean;
  fullWidth?: boolean;
  maxWidth?: number | string;
}

const Tooltip: React.FC<TooltipProps> = ({
  value,
  content,
  placement,
  showTooltip = true,
  fullWidth,
  maxWidth,
}) => {
  const [open, setOpen] = useState(false);
  const { x, y, refs, strategy, context } = useFloating({
    open,
    onOpenChange: setOpen,
    placement: placement || 'top',
    whileElementsMounted: autoUpdate,
    middleware: [
      offset(6),
      flip({ fallbackAxisSideDirection: 'start' }),
      shift({ padding: 12 }),
    ],
  });
  const hover = useHover(context);
  const { getReferenceProps, getFloatingProps } = useInteractions([hover]);
  const isOpened = showTooltip && open;
  return (
    <>
      <div ref={refs.setReference} {...getReferenceProps()}>
        <S.Wrapper>{value}</S.Wrapper>
      </div>
      {isOpened && (
        <S.MessageTooltip
          ref={refs.setFloating}
          style={{
            position: strategy,
            top: y ?? 0,
            left: x ?? 0,
            maxWidth: maxWidth ?? '380px',
            width: 'max-content',
            minWidth: fullWidth ? 'max-content' : 'initial',
            display: 'flex',
            alignItems: 'center',
            zIndex: 1000,
          }}
          {...getFloatingProps()}
        >
          {content}
        </S.MessageTooltip>
      )}
    </>
  );
};

export default Tooltip;
