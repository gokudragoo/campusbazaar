"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import { Phone, PriceTag, Stamp } from "./bits";

/**
 * A replay of a real negotiation from the app, in the app's own chat UI: listing card, question,
 * offer, counter, accept (the item becomes reserved), then a confirmed meetup. Plays while on
 * screen; with reduced motion it shows the finished deal without animating.
 */

// How long each beat stays on screen before the next one, in ms. The last beat fades out and loops.
const BEATS = [1100, 1200, 1400, 1200, 1500, 1800, 450, 1300, 1300, 1500, 5000, 600];
const LAST = BEATS.length - 1;
const FINAL = LAST - 1;

export function HaggleDemo() {
  const root = useRef<HTMLDivElement>(null);
  const [step, setStep] = useState(0);
  const [visible, setVisible] = useState(false);
  const [reduced, setReduced] = useState(false);

  useEffect(() => {
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    const sync = () => setReduced(mq.matches);
    sync();
    mq.addEventListener("change", sync);
    return () => mq.removeEventListener("change", sync);
  }, []);

  useEffect(() => {
    const el = root.current;
    if (!el) return;
    const io = new IntersectionObserver(([entry]) => setVisible(Boolean(entry?.isIntersecting)), { threshold: 0.3 });
    io.observe(el);
    return () => io.disconnect();
  }, []);

  useEffect(() => {
    if (reduced) {
      setStep(FINAL);
      return;
    }
    if (!visible) return;
    const timer = window.setTimeout(() => setStep((s) => (s >= LAST ? 0 : s + 1)), BEATS[step]);
    return () => window.clearTimeout(timer);
  }, [step, visible, reduced]);

  const typing = step === 2 || step === 4;
  const accepted = step >= 7;

  return (
    <div ref={root} className="haggle">
      <p className="sr-only">
        Example chat: a buyer asks about a Casio fx-991DE PLUS calculator listed at ₹650 and offers ₹500. The seller counters at
        ₹600, the buyer accepts, the calculator is reserved for them, and they confirm a meetup at the central library entrance.
      </p>
      <Phone className="haggle__phone">
        <div className="chat" aria-hidden="true">
          <div className="chat__status">
            <span>5:42</span>
            <span className="chat__icons">
              <i />
              <i />
              <i />
            </span>
          </div>
          <div className="chat__head">
            <svg viewBox="0 0 24 24" width="18" height="18">
              <path d="M15 5l-7 7 7 7" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            <span className="avatar">RM</span>
            <span className="chat__who">
              <strong>Rohan Mehta</strong>
              <small className={typing ? "is-typing" : undefined}>{typing ? "typing…" : "Hostel 4, Mechanical"}</small>
            </span>
          </div>
          <div className="chat__deal">
            <img src="/shots/casio.webp" alt="" width={36} height={36} />
            <span className="chat__deal-text">
              <b>Casio fx-991DE PLUS</b>
              <span>
                <PriceTag size="sm">₹650</PriceTag>
                <em className={accepted ? "is-reserved" : undefined}>{accepted ? "Reserved for you" : "Open to offers"}</em>
              </span>
            </span>
          </div>

          <div className={`chat__log${step === LAST ? " is-fading" : ""}`}>
            <Card mine>
              <div className="listing-card">
                <img src="/shots/casio.webp" alt="" width={44} height={44} />
                <span>
                  <b>Casio fx-991DE PLUS Scientific Calculator</b>
                  <span className="listing-card__meta">
                    <PriceTag size="sm">₹650</PriceTag> Good
                  </span>
                </span>
              </div>
            </Card>
            {step >= 1 && <Bubble mine>Hi! Is the calculator still available? Does it come with the cover?</Bubble>}
            {step === 2 && <Typing />}
            {step >= 3 && <Bubble>Yes, still available. Comes with the original cover.</Bubble>}
            {step >= 4 && (
              <Offer mine label="Offer" amount="₹500" status={step >= 5 ? "Countered" : "Waiting for Rohan"} flash={step === 5} />
            )}
            {step === 4 && <Typing />}
            {step >= 5 && (
              <Offer
                label="Counter offer"
                amount="₹600"
                note="In reply to ₹500"
                status={accepted ? "Accepted" : null}
                actions={!accepted}
                pressed={step === 6}
                stamp={accepted ? "Accepted" : null}
              />
            )}
            {step >= 8 && <p className="chat-note">Deal agreed at ₹600. The calculator is reserved for you. Pick a meetup spot.</p>}
            {step >= 9 && (
              <Card mine>
                <div className="card-label">
                  <PinIcon /> Meetup
                </div>
                {step >= 10 && <Stamp className="card-stamp">Confirmed</Stamp>}
                <b className="meetup__spot">Central library entrance</b>
                <span className="meetup__time">Sat 21 Nov, 5:00 PM</span>
                <span className="meetup__note">“I’ll be in a blue hoodie”</span>
              </Card>
            )}
          </div>

          <div className="chat__composer">
            <span>Message</span>
            <svg viewBox="0 0 24 24" width="16" height="16">
              <path d="M4 12l16-8-6 16-2.5-6.5z" fill="currentColor" />
            </svg>
          </div>
        </div>
      </Phone>
      <p className="haggle__caption">
        A real negotiation, replayed. Offer cards change state on both phones the moment someone taps Accept.
      </p>
    </div>
  );
}

function Bubble({ children, mine = false }: { children: ReactNode; mine?: boolean }) {
  return <p className={`bubble${mine ? " bubble--mine" : ""}`}>{children}</p>;
}

function Card({ children, mine = false }: { children: ReactNode; mine?: boolean }) {
  return <div className={`chat-card${mine ? " chat-card--mine" : ""}`}>{children}</div>;
}

function Typing() {
  return (
    <p className="bubble bubble--typing">
      <i />
      <i />
      <i />
    </p>
  );
}

function Offer(props: {
  label: string;
  amount: string;
  mine?: boolean;
  note?: string;
  status: string | null;
  actions?: boolean;
  pressed?: boolean;
  stamp?: string | null;
  flash?: boolean;
}) {
  return (
    <Card mine={props.mine}>
      <div className="card-label">
        <TagIcon /> {props.label}
      </div>
      {props.stamp && <Stamp className="card-stamp">{props.stamp}</Stamp>}
      <div className="offer__price">
        <PriceTag size="md">{props.amount}</PriceTag>
        <span>asking ₹650</span>
      </div>
      {props.note && <span className="offer__note">{props.note}</span>}
      {props.status && <b className={`offer__status${props.flash ? " is-flash" : ""}`}>{props.status}</b>}
      {props.actions && (
        <span className="offer__actions">
          <span className={`offer__accept${props.pressed ? " is-pressed" : ""}`}>Accept</span>
          <span>Counter</span>
          <span>Decline</span>
        </span>
      )}
    </Card>
  );
}

function TagIcon() {
  return (
    <svg viewBox="0 0 24 24" width="13" height="13">
      <path d="M3 12V4h8l10 10-8 8L3 12z" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
      <circle cx="7.5" cy="8.5" r="1.5" fill="currentColor" />
    </svg>
  );
}

function PinIcon() {
  return (
    <svg viewBox="0 0 24 24" width="13" height="13">
      <path d="M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21z" fill="none" stroke="currentColor" strokeWidth="2" />
      <circle cx="12" cy="9.5" r="2.4" fill="currentColor" />
    </svg>
  );
}
