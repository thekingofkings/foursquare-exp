
for condition = 1:5;
    dml5 = importdata('../res/distance-c0.200000-101s.txt');
    [~, ind] = sort(dml5(:,6), 'descend');
    dml5 = dml5(ind, :);

%     dml4 = dml4(dml4(:,4) > condition,:);
%     locwf4 = dml4(:,3);
%     locf4 = dml4(:,4);
%     colcEnt = dml4(:,5);
%     dl4 = dml4(:,6);
   


    dml5 = dml5(dml5(:,6) > condition,:);
    sum(dml5(:,7)==1)
    sum(dml5(:,7)==0)
    size(dml5)

    pbg_locen = dml5(:,3);
    locentro = dml5(:,4);
    pbg = dml5(:,5);
    freq = dml5(:,6);
    pbg_locen_td = dml5(:,7);
    td = dml5(:,8);
    friLabel = dml5(:,9);


    % Use the prec-recal function from Internet.
%     figure();
%     prec_rec( locwf5, dl5, 'plotROC', 0, 'holdFigure', 1, 'style', 'r--' );
%     hold on;
%     prec_rec( prod_colcEnt_cm, dl5, 'plotROC', 0, 'holdFigure', 1, 'style', 'g-' );
%     prec_rec( locm5, dl5, 'plotROC', 0, 'holdFigure', 1, 'style', 'b:');
%     prec_rec( locf5, dl5, 'plotROC', 0, 'holdFigure', 1, 'style', 'c--');
    % My own precision-recall plot function    
    figure();
    hold on;
    precisionRecallPlot( freq, friLabel, 'linestyle', '-', 'color', [0, 0, 0.8] );
    precisionRecallPlot( pbg, friLabel, 'r--' );
    precisionRecallPlot( locentro, friLabel, 'linestyle', '--', 'color', [0, 0.75, 0] );
    precisionRecallPlot( td, friLabel, 'linestyle', '--', 'color', [255, 215, 0] / 255 );
    precisionRecallPlot( pbg_locen, friLabel, 'linestyle', '-', 'color', [0.3, 0.6, 0.9] );
    precisionRecallPlot( pbg_locen_td, friLabel, 'linestyle', '-.', 'color', [0.5, 0.4, 0.9] );


%     title(num2str(condition));
    box on;
    grid on;
%     axis([0,1,0.5,1]);
    hline = findobj(gcf, 'type', 'line');
    set(hline, 'linewidth', 3);
    xlabel('Recall', 'fontsize', 20);
    ylabel('Precision', 'fontsize', 20);
    set(gca, 'linewidth', 2, 'fontsize', 18);
    legend({'Frequency', 'Personal', 'Global', 'Temp Depen', 'Per+Glo', 'Per+Glo+Tem'}, 'location', 'southwest');
    %    'Location ID measure', 'Location ID frequency'}, 'fontsize', 16);
    set(gcf, 'PaperUnits', 'inches');
    print(['pr-fs', num2str(condition), '.eps'], '-dpsc');
    system(['epstopdf pr-fs', num2str(condition), '.eps']);
%     saveas(gcf, ['dist-wsum-d30-u5000fgt',num2str(condition),'.png']);
%     saveas(gcf, ['freq-wfbu5000fgt',num2str(condition),'.fig']);
end