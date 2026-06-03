"""The optional learned exercise classifier (bible §9.3): a small causal 1-D Temporal CNN over a
32-frame window of the 61-d parity feature vector → 6 classes. <5 MB, runs on CPU TFLite on-device.

Constants MUST match ml/src/features/reference_features.py and io.vyayama.api (Angles/ExerciseType).
"""
from __future__ import annotations
import torch
import torch.nn as nn

FEATURE_DIM = 61            # 10 angles + 34 normalized kpts + 17 confidences
WINDOW = 32                 # frames (~1 s @30 fps)
CLASSES = ["NONE", "SQUAT", "PUSHUP", "LUNGE", "BICEP_CURL", "JUMPING_JACK"]
NUM_CLASSES = len(CLASSES)


class TCNBlock(nn.Module):
    def __init__(self, ch: int, dilation: int, k: int = 3):
        super().__init__()
        self.pad = (k - 1) * dilation
        self.conv = nn.Conv1d(ch, ch, k, padding=self.pad, dilation=dilation)
        self.bn = nn.BatchNorm1d(ch)
        self.relu = nn.ReLU()

    def forward(self, x):
        y = self.conv(x)[:, :, :-self.pad] if self.pad else self.conv(x)   # causal trim
        y = self.relu(self.bn(y))
        return x + y                                                       # residual


class ExerciseTCN(nn.Module):
    def __init__(self, in_dim: int = FEATURE_DIM, ch: int = 64, classes: int = NUM_CLASSES):
        super().__init__()
        self.inp = nn.Conv1d(in_dim, ch, 1)
        self.blocks = nn.Sequential(TCNBlock(ch, 1), TCNBlock(ch, 2), TCNBlock(ch, 4))
        self.head = nn.Linear(ch, classes)

    def forward(self, x):                  # x: (B, T, F)
        x = x.transpose(1, 2)              # (B, F, T)
        x = self.inp(x)
        x = self.blocks(x)
        x = x.mean(dim=2)                  # global average pool over time
        return self.head(x)                # logits (B, classes)
