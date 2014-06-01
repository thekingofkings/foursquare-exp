checkinsN = importdata('../res/checkinsN-dist');
friendsN = importdata('../res/friendsN-dist');

f1 = figure();
hold on;
hist(checkinsN, 50);
title('#Check-ins histrogram');


f2 = figure();
hold on;
hist(gca, friendsN, 50);
% set(gca, 'yscale', 'log');
title('#friends histogram');


f3 = figure();
hold on;
h1 = cdfplot(checkinsN);
set(h1, 'linewidth', 3, 'color', 'red');
h2 = cdfplot(friendsN);
set(h2, 'linewidth', 3', 'color', 'blue');
set(gca, 'xscale', 'log');
legend('#check-ins', '#friends');
