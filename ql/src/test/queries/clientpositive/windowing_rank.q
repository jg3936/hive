drop table over10k;

create table over10k(
           t tinyint,
           si smallint,
           i int,
           b bigint,
           f float,
           d double,
           bo boolean,
           s string,
	   ts timestamp, 
           dec decimal,  
           bin binary)
       row format delimited
       fields terminated by '|';

load data local inpath '../data/files/over10k' into table over10k;

select s, rank() over (partition by f order by t) from over10k limit 100;

select s, dense_rank() over (partition by ts order by i desc) from over10k limit 100;

select s, cume_dist() over (partition by bo order by b) from over10k limit 100;

select s, percent_rank() over (partition by dec order by f) from over10k limit 100;
