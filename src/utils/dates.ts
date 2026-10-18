/** Today's date in the user's time zone as YYYY-MM-DD (the format of <input type="date">). */
export function todayLocal(now: Date = new Date()): string {
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}
