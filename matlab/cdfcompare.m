a = importdata('../res/distance-c0.200000-101s.txt');


friLabel = a(:,9);



friendPair = a(a(:,9)==1,5);
nonfriendPair = a(a(:,9)==0,5);

l1 = cdfplot(friendPair);
hold on;
set(gca, 'xscale', 'log');
l2 = cdfplot(nonfriendPair);

set(l1, 'color', 'red', 'linewidth', 3);
set(l2, 'color', 'blue', 'linewidth', 3);


    
    