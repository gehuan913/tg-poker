export function Placeholder({ title, desc }: { title: string; desc: string }) {
  return (
    <>
      <div className="topbar"><div className="club-name">{title}</div></div>
      <div className="card"><div className="empty">{desc}</div></div>
    </>
  );
}
