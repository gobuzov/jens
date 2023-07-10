	org	32768
start:
	ei
	ds	80
	ld	a,r
	and	7
	out	(254),a
	add	a,50
	ld	b,a
lab:	halt
	djnz	lab
	jr	start
	
;k	equ	4
end:
	display "total ", end-start, " bytes"
	savesna 2.sna, 32768



