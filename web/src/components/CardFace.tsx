import { isRed, rankText, SUIT } from '../utils.ts';

interface Props {
  code: string;
  small?: boolean;
}

export function CardFace({ code, small }: Props) {
  if (!code) {
    return <div className={'card-face back' + (small ? ' small' : '')} />;
  }
  return (
    <div className={'card-face' + (isRed(code) ? ' red' : '') + (small ? ' small' : '')}>
      <div>{rankText(code)}</div>
      <div>{SUIT[code[1]] || ''}</div>
    </div>
  );
}

export function CardBack({ small }: { small?: boolean }) {
  return <div className={'card-face back' + (small ? ' small' : '')} />;
}
