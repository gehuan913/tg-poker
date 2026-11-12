export interface Me {
  userId: number;
  nick: string;
  avatar: string;
  balance: number;
  balanceText?: string;
  hands?: number;
  net?: number;
  netText?: string;
  avgText?: string;
  vpip?: number;
  pfr?: number;
  inviteCode?: string;
  vipLevel?: number;
  insuranceOptIn?: boolean;
  hasPayPwd?: boolean;
}

export interface SeatView {
  seat: number;
  userId?: number;
  nick: string;
  avatar: string;
  seated: boolean;
  sittingOut: boolean;
  folded: boolean;
  allIn: boolean;
  hasActed: boolean;
  dealer: boolean;
  acting: boolean;
  stack: number;
  bet: number;
  lastAction: string;
  lastAmount: number;
  cards?: string[];
  revealed?: boolean;
  inHand?: boolean;
}

export interface InsuranceQuote {
  available?: boolean;
  buyerSeat?: number;
  behindSeat?: number;
  outs: number;
  odds: number;
  oddsText: string;
  maxCoverage: number;
  coverage?: number;
  premium: number;
  premiumText: string;
  reason: string;
}

export interface InsuranceOrderView {
  handNo: number;
  nick: string;
  street: string;
  outs: number;
  odds: number;
  premiumText: string;
  coverageText: string;
  payoutText: string;
  result: string;
}

export interface ChatMsg {
  seat: number;
  userId: number;
  nick: string;
  text: string;
  at: number;
}

export interface TableView {
  tableId: string;
  name: string;
  variant: string;
  variantLabel: string;
  clubId: number;
  smallBlind: number;
  bigBlind: number;
  ante: number;
  anteMode: string;
  anteLabel: string;
  straddle?: boolean;
  straddleAmount?: number;
  straddleSeat?: number;
  minBuyIn: number;
  maxBuyIn: number;
  maxSeats: number;
  stage: string;
  handId?: string;
  handNo: number;
  buttonSeat: number;
  actingSeat: number;
  mySeat: number;
  role: string;
  pot: number;
  currentBet: number;
  minRaise: number;
  deadline: number;
  serverTime: number;
  showdown: boolean;
  canStart: boolean;
  insuranceEnabled: boolean;
  serverSeedHash?: string;
  lastServerSeed?: string;
  verifyCode?: string;
  clientSeeds?: string[];
  board: string[];
  myCards: string[];
  seats: SeatView[];
  log: string[];
  chat?: ChatMsg[];
  runningMinutes?: number;
  seq: number;
  actionSeconds: number;
  spectators: number;
  insurance?: InsuranceQuote;
  insurances?: InsuranceOrderView[];
}

export interface LobbyItem {
  tableId: string;
  name: string;
  variant: string;
  variantLabel: string;
  ante: number;
  anteLabel: string;
  straddle?: boolean;
  clubId: number;
  insuranceEnabled: boolean;
  smallBlind: number;
  bigBlind: number;
  minBuyIn: number;
  maxBuyIn: number;
  seated: number;
  maxSeats: number;
  stage: string;
  avgVpip: number;
  runningMinutes: number;
  handNo: number;
}

export interface LedgerItem {
  id: number;
  direction: number;
  amount: number;
  bizType: string;
  memo?: string;
  createdAt?: string;
}

export interface TableLedgerRow {
  seat: number;
  userId: number;
  nick: string;
  avatar: string;
  buyIn?: number;
  buyInText?: string;
  hands: number;
  vpip: number;
  pfr: number;
  net: number;
  netText: string;
  seated: boolean;
}

export interface HandItem {
  handId: string;
  seat: number;
  holeCards?: string;
  net: number;
  startStack: number;
  endStack: number;
  insuranceNet?: number;
  variant?: string;
  board?: string;
  startedAt?: string;
}

export interface StatsBlock {
  variant: string;
  hands: number;
  net: number;
  netText: string;
  insuranceNetText: string;
  avgText: string;
  vpip?: number;
  pfr?: number;
  rounds?: number;
  winRate?: number;
  bankerRounds?: number;
}

export interface ActivityItem {
  code: string;
  name: string;
  icon: string;
  description: string;
  link: string;
  rewardAmount: number;
  rewardText: string;
  claimed: boolean;
}

export interface ClubItem {
  id: number;
  name: string;
  avatar: string;
  notice: string;
  joinType: string;
  members: number;
  minBuyIn: number;
  minBuyInText: string;
  insuranceEnabled: boolean;
  treasuryText: string;
  role?: string;
}

export interface LessonItem {
  id: number;
  category: string;
  title: string;
  summary: string;
  mediaType: string;
  mediaUrl: string;
  durationSec: number;
  likes: number;
  views: number;
  content: string;
}

export interface QuizItem {
  id: number;
  category: string;
  question: string;
  options: string;
  explain?: string;
}

export interface NiuniuSeat {
  seat: number;
  userId?: number;
  nick: string;
  avatar: string;
  seated: boolean;
  stack: number;
  stackText: string;
  bet: number;
  betText: string;
  betPlaced: boolean;
  banker: boolean;
  result: number;
  resultText: string;
  cards?: string[];
  niu?: number;
  niuLabel?: string;
  multiplier?: number;
}

export interface NiuniuView {
  tableId: string;
  name: string;
  stage: string;
  deadline: number;
  serverTime: number;
  bankerSeat: number;
  roundNo: number;
  mySeat: number;
  maxBet: number;
  betSeconds: number;
  seats: NiuniuSeat[];
  log: string[];
  canBet: boolean;
  wantBanker?: boolean;
  bankerMin?: number;
  queueCount?: number;
  lastNiuNiuSeat?: number;
  canApplyBanker?: boolean;
}
