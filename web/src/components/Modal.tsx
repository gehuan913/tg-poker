import type { ReactNode } from 'react';

interface Props {
  title: string;
  children: ReactNode;
  onClose: () => void;
  onOk?: () => boolean | void | Promise<boolean | void>;
  okText?: string;
}

export function Modal({ title, children, onClose, onOk, okText = '确定' }: Props) {
  return (
    <div className="modal-mask" onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="modal">
        <h3>{title}</h3>
        {children}
        <div className="modal-actions">
          <button className="btn ghost" onClick={onClose}>取消</button>
          <button
            className="btn primary"
            onClick={async () => {
              const r = onOk ? await onOk() : true;
              if (r !== false) onClose();
            }}
          >
            {okText}
          </button>
        </div>
      </div>
    </div>
  );
}
