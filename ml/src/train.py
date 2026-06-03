"""Train the optional exercise TCN on feature windows (bible §9.3). Optional/additive — the
rule-based classifier ships the demo; this adds robustness/scale.

Run:  python ml/src/data/keypoints.py <root> dataset.npz
      python ml/src/train.py --data dataset.npz --epochs 30
"""
from __future__ import annotations
import sys, os, argparse
import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import torch
from torch.utils.data import DataLoader, TensorDataset, random_split
from models.tcn import ExerciseTCN, CLASSES  # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default="dataset.npz")
    ap.add_argument("--epochs", type=int, default=30)
    ap.add_argument("--batch", type=int, default=64)
    ap.add_argument("--lr", type=float, default=1e-3)
    ap.add_argument("--out", default="runs/best.pt")
    args = ap.parse_args()

    d = np.load(args.data)
    X = torch.tensor(d["X"], dtype=torch.float32)
    y = torch.tensor(d["y"], dtype=torch.long)
    ds = TensorDataset(X, y)
    n_val = max(1, int(len(ds) * 0.2))
    train_ds, val_ds = random_split(ds, [len(ds) - n_val, n_val],
                                    generator=torch.Generator().manual_seed(0))
    tl = DataLoader(train_ds, batch_size=args.batch, shuffle=True, drop_last=True)
    vl = DataLoader(val_ds, batch_size=args.batch)

    dev = "cuda" if torch.cuda.is_available() else "cpu"
    model = ExerciseTCN().to(dev)
    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=1e-4)
    loss_fn = torch.nn.CrossEntropyLoss()
    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)

    best = 0.0
    for ep in range(args.epochs):
        model.train()
        for xb, yb in tl:
            xb, yb = xb.to(dev), yb.to(dev)
            opt.zero_grad(); loss = loss_fn(model(xb), yb); loss.backward(); opt.step()
        # eval
        model.eval(); correct = total = 0
        with torch.no_grad():
            for xb, yb in vl:
                pred = model(xb.to(dev)).argmax(1).cpu()
                correct += (pred == yb).sum().item(); total += len(yb)
        acc = correct / max(1, total)
        print(f"epoch {ep + 1}/{args.epochs}  val_acc={acc:.3f}")
        if acc > best:
            best = acc
            torch.save({"model": model.state_dict(), "classes": CLASSES, "acc": acc}, args.out)
    print(f"best val_acc={best:.3f}  saved {args.out}")


if __name__ == "__main__":
    main()
