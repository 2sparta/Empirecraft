#!/usr/bin/env python3
"""Plot only the redacted server timing summary; never opens a raw uploaded profile."""
import json
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
ROOT=Path(__file__).resolve().parent
def main():
    report=json.loads((ROOT/'audit/server-profile-summary.json').read_text());windows=report['windows'];x=range(1,len(windows)+1)
    fig,axes=plt.subplots(3,1,figsize=(9,7.5),sharex=True,layout='constrained')
    for ax in axes:
        ax.grid(axis='y',alpha=.2);ax.spines[['top','right']].set_visible(False)
    axes[0].plot(x,[w['tps'] for w in windows],color='#277251',linewidth=2);axes[0].set_ylim(19,20.5);axes[0].set_ylabel('TPS');axes[0].set_title('Сервер: 32 хвилини наданого профілю',loc='left',fontweight='bold')
    axes[1].plot(x,[w['mspt_median'] for w in windows],color='#28679e',linewidth=2);axes[1].set_ylim(0,14);axes[1].set_ylabel('Медіана тіку, мс')
    axes[2].plot(x,[w['mspt_max'] for w in windows],color='#ad5847',linewidth=1.5);axes[2].axhline(50,color='#646464',linestyle='--',linewidth=1,label='Бюджет тіку 50 мс');axes[2].set_ylim(0,420);axes[2].set_ylabel('Найдовший тік, мс');axes[2].set_xlabel('Хвилина профілю');axes[2].legend(loc='lower right',frameon=False)
    fig.suptitle('Запис закінчився до зависання після /back; клієнтських вимірювань у ньому немає.',fontsize=10)
    target=ROOT/'dist/Empirecraft-Server-Profile-2026-11-13.png';fig.savefig(target,dpi=160);plt.close(fig);print('PROFILE_PLOT_PASS',target.name)
if __name__=='__main__':main()
