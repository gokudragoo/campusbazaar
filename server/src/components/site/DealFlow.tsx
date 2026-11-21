"use client";

import { useEffect, useRef, useState } from "react";
import { Phone, Stamp } from "./bits";

const STEPS = [
  {
    title: "List it in under a minute",
    body: "Take up to five photos. Gemini looks at them and drafts the title, category, condition and a fair used price in rupees. Change anything you like, set your price and where you'd prefer to meet, and publish to your campus.",
    shot: "/shots/03-gemini-draft.webp",
    alt: "Sell screen with an AI listing draft for a Casio calculator, priced ₹500 to ₹800",
  },
  {
    title: "Haggle in chat",
    body: "Buyers tap Chat with seller and the listing card pins the conversation. Send an offer, counter it, accept it. Accepting reserves the item for that buyer, so nobody else can grab it from under them.",
    shot: "/shots/06-offer-accepted.webp",
    alt: "Chat with an offer of ₹500 countered at ₹600 and stamped accepted",
  },
  {
    title: "Meet somewhere public on campus",
    body: "Propose a spot and a time with a meetup card, or share a pin on the map. The other side confirms with one tap, and both of you can see the plan at the top of the chat.",
    shot: "/shots/07-meetup-confirmed.webp",
    alt: "Meetup card for the central library entrance, Saturday 5 PM, stamped confirmed",
  },
  {
    title: "Mark it sold, rate each other",
    body: "After the handover the seller marks the item sold and picks the buyer. You both leave a rating, so the next student knows who they're dealing with.",
    shot: "/shots/08-sold-and-rated.webp",
    alt: "Chat after the sale with a five-star rating notification",
  },
] as const;

export function DealFlow() {
  const [active, setActive] = useState(0);
  const refs = useRef<(HTMLLIElement | null)[]>([]);

  useEffect(() => {
    const io = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) setActive(Number((entry.target as HTMLElement).dataset.index));
        }
      },
      { rootMargin: "-45% 0px -45% 0px" },
    );
    refs.current.forEach((el) => el && io.observe(el));
    return () => io.disconnect();
  }, []);

  return (
    <div className="flow">
      <ol className="flow__steps">
        {STEPS.map((step, i) => (
          <li
            key={step.title}
            ref={(el) => {
              refs.current[i] = el;
            }}
            data-index={i}
            className={`flow__step${active === i ? " is-active" : ""}`}
          >
            <span className="flow__num" aria-hidden="true">
              {i + 1}
            </span>
            <h3>{step.title}</h3>
            <p>{step.body}</p>
            <Phone className="flow__inline-phone">
              <img src={step.shot} alt={step.alt} width={540} height={1200} loading="lazy" />
            </Phone>
          </li>
        ))}
      </ol>
      <div className="flow__stage" aria-hidden="true">
        <Phone className="flow__phone">
          {STEPS.map((step, i) => (
            <img
              key={step.shot}
              src={step.shot}
              alt=""
              width={540}
              height={1200}
              loading={i === 0 ? "eager" : "lazy"}
              className={active === i ? "is-active" : undefined}
            />
          ))}
        </Phone>
        <Stamp className={`flow__sold${active === STEPS.length - 1 ? " is-down" : ""}`}>Sold</Stamp>
      </div>
    </div>
  );
}
